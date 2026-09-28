"""큐 소진 부하 테스트 — 워커 수에 따라 처리량이 비례하는지, 회수 루프 오버헤드가 얼마인지 잰다.

사전: docker compose --profile load up -d --build   (스크립트가 워커를 --sleep 값으로 재생성한다)
실행: python load-test.py [--jobs 120] [--sleep 1] [--workers 1,2,3]

각 워커 수마다: 해당 대수만 살려두고 → 스트림 비우고 → N건 한꺼번에 XADD → lag·pending이 0이 될 때까지 2초마다 기록.
결과는 표로 출력하고 data/load-<workers>w.csv 에 시계열을 남긴다 (Grafana 대시보드에서도 같은 구간을 볼 수 있다).
"""
import argparse
import json
import os
import subprocess
import time

import redis

HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
STREAM, DLQ, GROUP = "nonverbal-analysis-jobs", "nonverbal-analysis-dlq", "analysis-group"
ALL_WORKERS = ["a", "b", "c"]
CB_LOG = os.path.join(HERE, "data", "callbacks.log")

r = redis.Redis(host="127.0.0.1", port=6389, password="chaos")


def sh(*cmd):
    res = subprocess.run(cmd, capture_output=True, text=True)
    return (res.stdout + res.stderr).strip()


def cb_count():
    return len(open(CB_LOG).read().strip().splitlines()) if os.path.exists(CB_LOG) else 0


def set_workers(n, sleep_s):
    """정확히 n대만 실행 상태로 맞춘다 (a, b, c 순). ANALYSIS_SLEEP을 compose 치환에 넘겨야 재생성 시 기본값(20s)으로 되돌아가지 않는다."""
    os.environ["ANALYSIS_SLEEP"] = str(sleep_s)
    for i, w in enumerate(ALL_WORKERS):
        name = f"chaos-worker-{w}"
        if i < n:
            sh("docker", "compose", "--project-directory", HERE, "--profile", "load", "up", "-d", f"worker-{w}")
        else:
            sh("docker", "stop", name)
    # 죽은 consumer 이름이 group에 남아 있으면 지운다 (consumer 수 집계를 정확히 하기 위해)
    if r.exists(STREAM):
        for c in r.xinfo_consumers(STREAM, GROUP):
            if c["pending"] == 0 and c["name"].decode().replace("analysis-consumer-worker-", "") not in ALL_WORKERS[:n]:
                r.xgroup_delconsumer(STREAM, GROUP, c["name"])
    deadline = time.time() + 60
    while time.time() < deadline:
        alive = [c["name"].decode() for c in r.xinfo_consumers(STREAM, GROUP)] if r.exists(STREAM) else []
        if len(alive) == n:
            return alive
        time.sleep(1)
    raise SystemExit(f"워커 {n}대가 group에 등록되지 않음: {alive}")


def group_stats():
    g = r.xinfo_groups(STREAM)[0]
    return g["lag"] or 0, g["pending"]


def run(n_workers, jobs, sleep_s):
    alive = set_workers(n_workers, sleep_s)
    # 스트림을 지우면 group도 사라지므로 워커가 재생성할 때까지 잠깐 기다린다 → 대신 XTRIM으로 비운다
    r.xtrim(STREAM, 0)
    r.delete(DLQ)
    base = cb_count()
    print(f"\n=== 워커 {n_workers}대 ({', '.join(a.replace('analysis-consumer-', '') for a in alive)}), 작업 {jobs}건, 분석 {sleep_s}s/건 ===", flush=True)

    t0 = time.time()
    pipe = r.pipeline()
    for i in range(jobs):
        pipe.xadd(STREAM, {"job": json.dumps({"speechId": 1000 + i, "s3FileKey": f"load/{i}.mp4"})})
    pipe.execute()
    print(f"XADD {jobs}건 {time.time() - t0:.2f}s", flush=True)

    rows, last_print = [], 0
    while True:
        lag, pending = group_stats()
        done = cb_count() - base
        elapsed = time.time() - t0
        rows.append((round(elapsed, 1), lag, pending, done))
        if elapsed - last_print >= 10:
            print(f"  T+{elapsed:5.1f}s  lag={lag:4d}  pending={pending:2d}  done={done:4d}", flush=True)
            last_print = elapsed
        if done >= jobs and lag == 0 and pending == 0:
            break
        if elapsed > jobs * sleep_s * 2 + 60:
            print("  !! 시간 초과")
            break
        time.sleep(2)
    drain = time.time() - t0
    theo = jobs * sleep_s / n_workers
    with open(os.path.join(HERE, "data", f"load-{n_workers}w.csv"), "w") as f:
        f.write("elapsed_s,lag,pending,done\n" + "\n".join(",".join(map(str, x)) for x in rows) + "\n")
    return {"workers": n_workers, "drain_s": drain, "throughput": jobs / drain, "theoretical_s": theo,
            "overhead_pct": (drain - theo) / theo * 100, "dlq": r.xlen(DLQ)}


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--jobs", type=int, default=120)
    ap.add_argument("--sleep", type=float, default=1.0, help="컨테이너에 준 ANALYSIS_SLEEP과 같은 값 (이론치 계산용)")
    ap.add_argument("--workers", default="1,2,3")
    a = ap.parse_args()
    results = [run(int(n), a.jobs, a.sleep) for n in a.workers.split(",")]
    set_workers(2, 20)  # 기본 상태(a, b / 분석 20s)로 복귀
    print("\n| 워커 수 | 소진 시간 | 처리량 | 이론치(작업×분석시간/워커) | 오버헤드 | DLQ |")
    print("|---|---|---|---|---|---|")
    for x in results:
        print(f"| {x['workers']} | {x['drain_s']:.1f}s | {x['throughput']:.2f} 건/s | {x['theoretical_s']:.1f}s | +{x['overhead_pct']:.0f}% | {x['dlq']} |")
    base = results[0]["drain_s"]
    print("\n스케일 효율(1대 대비): " + ", ".join(f"{x['workers']}대 {base / x['drain_s']:.2f}x" for x in results))
