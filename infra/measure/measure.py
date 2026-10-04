#!/usr/bin/env python3
"""@Async vs Redis Streams 측정 드라이버 (06-async-vs-streams.md 3절). 표준 라이브러리만 쓴다.

  python measure.py up streams|async [--workers 2] [--pool core,max,queue] [--sleep 20]
  python measure.py burst 100            # 동시 요청 → 지연·상태코드, 소진 시간, Prometheus 피크값
  python measure.py kill-spring          # 10건 1초 간격, 5번째 직후 app kill → 자동 재시작 → 유실/고착/중복 집계
  python measure.py status               # DB 상태 분포
  python measure.py down
결과는 data/<scenario>-<mode>-<ts>.json 과 stdout 요약.
"""
import base64, hashlib, hmac, json, os, subprocess, sys, threading, time, urllib.request, urllib.error
from datetime import datetime

HERE = os.path.dirname(os.path.abspath(__file__))
COMPOSE = ["docker", "compose", "-f", os.path.join(HERE, "docker-compose.yml")]
BASE = "http://127.0.0.1:18080"
PROM = "http://127.0.0.1:19090"
SECRET = b"measure-jwt-secret-at-least-32-bytes-long"
MODE_FILE = os.path.join(HERE, "data", ".mode")


def sh(*args, check=True, capture=False, env=None):
    e = dict(os.environ, **(env or {}))
    r = subprocess.run(args, check=check, env=e, text=True, capture_output=capture)
    return r.stdout if capture else None


def log(msg):
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}", flush=True)


def jwt(sub=1, category="access", hours=1):
    b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b"=").decode()
    h = b64(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    now = int(time.time())
    p = b64(json.dumps({"sub": str(sub), "category": category, "iat": now, "exp": now + hours * 3600}).encode())
    sig = b64(hmac.new(SECRET, f"{h}.{p}".encode(), hashlib.sha256).digest())
    return f"{h}.{p}.{sig}"


TOKEN = jwt()


def post_nonverbal(speech_id, timeout=30):
    req = urllib.request.Request(f"{BASE}/api/speech/nonverbal/{speech_id}", method="POST",
                                 headers={"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"})
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            code = r.status
    except urllib.error.HTTPError as e:
        code = e.code
    except Exception as e:
        code = type(e).__name__
    return code, time.perf_counter() - t0


def sql(query):
    out = sh(*COMPOSE, "exec", "-T", "db", "mysql", "-uroot", "-pm1234", "-N", "-B", "speechmate", "-e", query, capture=True)
    return [line.split("\t") for line in out.strip().splitlines() if line.strip()]


def fresh_ids(n):
    return [int(r[0]) for r in sql(f"SELECT id FROM speech WHERE non_verbal_status='NOT_STARTED' ORDER BY id LIMIT {n}")]


def status_of(ids):
    rows = sql(f"SELECT non_verbal_status, COUNT(*) FROM speech WHERE id IN ({','.join(map(str, ids))}) GROUP BY 1")
    return {r[0]: int(r[1]) for r in rows}


def prom_max(expr, start, end):
    q = urllib.parse.urlencode({"query": expr, "start": start, "end": end, "step": "5s"})
    try:
        with urllib.request.urlopen(f"{PROM}/api/v1/query_range?{q}", timeout=5) as r:
            data = json.load(r)["data"]["result"]
        vals = [float(v[1]) for s in data for v in s["values"] if v[1] not in ("NaN", "+Inf")]
        return max(vals) if vals else None
    except Exception as e:
        return f"err:{e}"


def app_log_count(pattern, since):
    out = sh(*COMPOSE, "logs", "--no-log-prefix", "--since", since, "app", capture=True, check=False) or ""
    return sum(1 for line in out.splitlines() if pattern in line)


def wait_health(timeout=120):
    t0 = time.time()
    while time.time() - t0 < timeout:
        try:
            if b'"UP"' in urllib.request.urlopen(f"{BASE}/actuator/health", timeout=2).read():
                return time.time() - t0
        except Exception:
            pass
        time.sleep(0.5)
    raise SystemExit("앱이 올라오지 않음")


def mode():
    return open(MODE_FILE).read().strip() if os.path.exists(MODE_FILE) else "?"


def save(name, data):
    os.makedirs(os.path.join(HERE, "data"), exist_ok=True)
    path = os.path.join(HERE, "data", f"{name}-{mode()}-{datetime.now().strftime('%H%M%S')}.json")
    json.dump(data, open(path, "w"), ensure_ascii=False, indent=2)
    log(f"저장: {os.path.relpath(path)}")


# ---------------------------------------------------------------- commands
def cmd_up(args):
    m = args[0]
    assert m in ("streams", "async")
    env = {"APP": m}
    if "--workers" in args: env["WORKERS"] = args[args.index("--workers") + 1]
    if "--sleep" in args: env["ANALYSIS_SLEEP"] = args[args.index("--sleep") + 1]
    if "--pool" in args:
        c, x, q = args[args.index("--pool") + 1].split(",")
        env.update(POOL_CORE=c, POOL_MAX=x, POOL_QUEUE=q)
    sh(*COMPOSE, "up", "-d", "--build", "--remove-orphans", env=env)
    os.makedirs(os.path.join(HERE, "data"), exist_ok=True)
    open(MODE_FILE, "w").write(m)
    log(f"앱 기동 대기… ({m}, env {env})")
    log(f"앱 UP ({wait_health():.1f}s). Grafana http://127.0.0.1:13000  Prometheus {PROM}")


def cmd_burst(args):
    n = int(args[0]) if args else 100
    ids = fresh_ids(n)
    assert len(ids) == n, f"NOT_STARTED 행 부족: {len(ids)}"
    results = [None] * n
    gate = threading.Barrier(n)

    def one(i):
        gate.wait()
        results[i] = post_nonverbal(ids[i])

    start = time.time()
    ts = [threading.Thread(target=one, args=(i,)) for i in range(n)]
    [t.start() for t in ts]; [t.join() for t in ts]
    fire_dur = time.time() - start
    codes = {}
    for c, _ in results: codes[str(c)] = codes.get(str(c), 0) + 1
    lat = sorted(l for _, l in results)
    log(f"{n}건 발사 완료 {fire_dur:.1f}s — 상태코드 {codes}, 지연 avg {sum(lat)/n*1000:.0f}ms p95 {lat[int(n*0.95)-1]*1000:.0f}ms max {lat[-1]*1000:.0f}ms")

    timeline = []
    t_drain = None
    while time.time() - start < 900:
        st = status_of(ids)
        timeline.append({"t": round(time.time() - start, 1), **st})
        log(f"  +{time.time()-start:5.1f}s {st}")
        if st.get("IN_PROGRESS", 0) == 0:
            t_drain = time.time() - start
            break
        time.sleep(5)
    end = time.time()
    peaks = {
        "executor_active_max": prom_max('executor_active_threads{name="applicationTaskExecutor"}', start, end),
        "executor_queued_max": prom_max('executor_queued_tasks{name="applicationTaskExecutor"}', start, end),
        "tomcat_busy_max": prom_max("tomcat_threads_busy_threads", start, end),
        "hikari_pending_max": prom_max("hikaricp_connections_pending", start, end),
        "heap_used_max_mb": (lambda v: round(v / 1048576) if isinstance(v, float) else v)(prom_max('sum(jvm_memory_used_bytes{area="heap"})', start, end)),
        "stream_lag_max": prom_max('max(redis_stream_group_lag{stream="nonverbal-analysis-jobs"})', start, end),
        "stream_pending_max": prom_max('max(redis_stream_group_messages_pending{stream="nonverbal-analysis-jobs"})', start, end),
    }
    summary = {"mode": mode(), "n": n, "fire_seconds": round(fire_dur, 2), "codes": codes,
               "latency_ms": {"avg": round(sum(lat) / n * 1000), "p95": round(lat[int(n * 0.95) - 1] * 1000), "max": round(lat[-1] * 1000)},
               "drain_seconds": None if t_drain is None else round(t_drain, 1), "final": status_of(ids), "peaks": peaks, "timeline": timeline}
    log(f"소진 {summary['drain_seconds']}s, 최종 {summary['final']}, 피크 {peaks}")
    save("burst", summary)


def cmd_kill_spring(args):
    n, kill_after = 10, 5
    ids = fresh_ids(n)
    since = datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%S")
    start = time.time()
    sent = []
    for i, sid in enumerate(ids):
        code, lat = post_nonverbal(sid)
        sent.append({"t": round(time.time() - start, 2), "id": sid, "code": code})
        log(f"  요청 {i+1}/{n} speech={sid} → {code}")
        if i + 1 == kill_after:
            time.sleep(1)
            log(f"  상태 {status_of(ids)}  → docker kill app")
            t_kill = time.time() - start
            # docker kill 은 '수동 중지'라 restart: always 가 되살리지 않는다 → 바로 start 해서
            # 프로세스 크래시 때 재시작 정책이 하는 것과 같은 다운타임(JVM 부팅 시간)을 만든다
            sh(*COMPOSE, "kill", "app"); sh(*COMPOSE, "start", "app")
            down = wait_health(180)
            t_up = time.time() - start
            log(f"  앱 복귀 +{t_up:.1f}s (다운 {t_up - t_kill:.1f}s)")
        time.sleep(1)
    # 남은 작업이 끝나길 기다린다. async 모드에선 고착된 IN_PROGRESS 가 끝나지 않으므로 상한을 둔다
    timeline = []
    while time.time() - start < 240:
        st = status_of(ids)
        timeline.append({"t": round(time.time() - start, 1), **st})
        log(f"  +{time.time()-start:5.1f}s {st}")
        if st.get("IN_PROGRESS", 0) == 0:
            break
        time.sleep(5)
    final = status_of(ids)
    accepted = [s["id"] for s in sent if s["code"] == 200]
    dup_ignored = app_log_count("토큰 불일치", since) + app_log_count("콜백 중복 수신", since)
    summary = {"mode": mode(), "n": n, "kill_after": kill_after, "kill_at": round(t_kill, 1), "app_up_at": round(t_up, 1),
               "downtime_seconds": round(t_up - t_kill, 1), "requests": sent, "accepted": len(accepted),
               "final": final, "stuck_in_progress": final.get("IN_PROGRESS", 0),
               "duplicate_callbacks_ignored": dup_ignored, "timeline": timeline}
    log(f"최종 {final} | 수락 {len(accepted)}건 중 COMPLETED {final.get('COMPLETED',0)}, 고착(IN_PROGRESS) {summary['stuck_in_progress']}, 중복 콜백 무시 {dup_ignored}")
    save("kill-spring", summary)


def cmd_status(args):
    print(sql("SELECT non_verbal_status, COUNT(*) FROM speech GROUP BY 1"))


def cmd_down(args):
    sh(*COMPOSE, "down", "-v", "--remove-orphans")


if __name__ == "__main__":
    import urllib.parse
    cmd, *rest = sys.argv[1:] or ["help"]
    {"up": cmd_up, "burst": cmd_burst, "kill-spring": cmd_kill_spring, "status": cmd_status, "down": cmd_down}.get(cmd, lambda a: print(__doc__))(rest)
