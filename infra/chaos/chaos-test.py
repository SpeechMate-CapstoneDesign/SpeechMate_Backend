"""비언어 분석 파이프라인 장애 주입 실험 드라이버.

사전: docker compose up -d --build   (이 디렉토리에서)
실행: python chaos-test.py            (redis-py 필요: pip install redis)

시나리오
  1. 처리 중 워커 컨테이너 docker kill      → 다른 워커가 PEL을 회수해 완료하는가, 몇 초 걸리는가
  2. 같은 작업이 워커를 반복 크래시시킴       → 3회 전달 후 DLQ로 격리되고 FAILED 콜백이 오는가 (restart 정책으로 되살아나는 워커 포함)
  3. 처리 중 Redis 재시작(AOF)             → consumer group·PEL이 보존되고 작업이 끝까지 가는가, 콜백이 중복되는가
"""
import json
import os
import subprocess
import sys
import time

import redis

HERE = os.path.dirname(os.path.abspath(__file__))
CB_LOG = os.path.join(HERE, "data", "callbacks.log")
STREAM, DLQ, GROUP = "nonverbal-analysis-jobs", "nonverbal-analysis-dlq", "analysis-group"
IDLE_S = int(os.getenv("RECLAIM_IDLE_MS", 30000)) / 1000

r = redis.Redis(host="127.0.0.1", port=6389, password="chaos")
T0 = time.time()
timeline = []


def log(msg):
    line = f"T+{time.time() - T0:6.1f}s  {msg}"
    timeline.append(line)
    print(line, flush=True)


def sh(*cmd):
    return subprocess.run(cmd, capture_output=True, text=True).stdout.strip()


def pel():
    return [(p["consumer"].decode().replace("analysis-consumer-", ""), p["times_delivered"])
            for p in r.xpending_range(STREAM, GROUP, "-", "+", 10)]


def callbacks(since_line):
    lines = open(CB_LOG).read().strip().splitlines() if os.path.exists(CB_LOG) else []
    return [tuple(l.split()) for l in lines[since_line:]]


def cb_count():
    return len(open(CB_LOG).read().strip().splitlines()) if os.path.exists(CB_LOG) else 0


def publish(speech_id):
    r.xadd(STREAM, {"job": json.dumps({"speechId": speech_id, "s3FileKey": f"videos/{speech_id}.mp4"})})
    log(f"XADD speech {speech_id}")


def wait(cond, timeout, every=0.5):
    end = time.time() + timeout
    while time.time() < end:
        v = cond()
        if v:
            return v
        time.sleep(every)
    return None


def watch_pel_changes(until, timeout):
    """PEL 소유자/전달횟수 변화를 타임라인에 남기며 until()이 참이 될 때까지 기다린다."""
    last, end = None, time.time() + timeout
    while time.time() < end:
        cur = pel()
        if cur != last:
            log(f"PEL = {cur}")
            last = cur
        if until():
            return True
        time.sleep(0.5)
    return False


def restarts(name):
    return int(sh("docker", "inspect", "-f", "{{.RestartCount}}", name) or 0)


def setup():
    r.delete(STREAM, DLQ)
    ok = wait(lambda: len(r.xinfo_consumers(STREAM, GROUP)) >= 2 if r.exists(STREAM) else False, 60)
    assert ok, "워커 2대가 consumer group에 등록되지 않음 (docker compose up -d --build 했는지 확인)"
    log(f"준비 완료: consumers = {[c['name'].decode() for c in r.xinfo_consumers(STREAM, GROUP)]}, idle 기준 {IDLE_S:.0f}s")


def scenario_1_docker_kill():
    print("\n=== 시나리오 1: 처리 중 워커 컨테이너 docker kill ===")
    base = cb_count()
    publish(1)
    holder = wait(lambda: pel()[0][0] if pel() else None, 15)
    time.sleep(3)
    log(f"PEL = {pel()}  ({holder}가 분석 중)")
    sh("docker", "kill", f"chaos-{holder}")
    t_kill = time.time()
    log(f"docker kill chaos-{holder}  → PEL = {pel()} (ACK 없이 잔류)")
    ok = watch_pel_changes(lambda: len(callbacks(base)) >= 1, IDLE_S + 60)
    cbs = callbacks(base)
    t_cb = float(cbs[0][0]) if cbs else None
    log(f"콜백 = {[c[1:] for c in cbs]}  PEL = {pel()}  DLQ = {r.xlen(DLQ)}")
    recovery = (t_cb - t_kill) if t_cb else None
    log(f"복구 소요: kill → COMPLETED 콜백 {recovery:.1f}s (idle 기준 {IDLE_S:.0f}s + 폴링 + 분석 {os.getenv('ANALYSIS_SLEEP', 20)}s)" if recovery else "복구 실패")
    sh("docker", "compose", "up", "-d", holder)  # docker kill은 restart 정책 대상이 아니라 직접 되살림
    log(f"{holder} 재기동")
    return ok and cbs and cbs[0][1:] == ("1", "COMPLETED") and not pel() and r.xlen(DLQ) == 0, recovery


def scenario_2_repeated_crash():
    print("\n=== 시나리오 2: 같은 작업이 워커를 반복 크래시 → DLQ ===")
    base = cb_count()
    ra, rb = restarts("chaos-worker-a"), restarts("chaos-worker-b")
    publish(666)
    ok = watch_pel_changes(lambda: r.xlen(DLQ) >= 1 and len(callbacks(base)) >= 1, 3 * IDLE_S + 120)
    cbs = callbacks(base)
    dlq = r.xrange(DLQ)
    log(f"DLQ = {[(e[1][b'reason'].decode(), json.loads(e[1][b'job'])['speechId']) for e in dlq]}  콜백 = {[c[1:] for c in cbs]}  PEL = {pel()}")
    log(f"컨테이너 재시작 횟수: worker-a +{restarts('chaos-worker-a') - ra}, worker-b +{restarts('chaos-worker-b') - rb}")
    return ok and cbs and cbs[-1][1:] == ("666", "FAILED") and not pel() and len(dlq) == 1


def scenario_3_redis_restart():
    print("\n=== 시나리오 3: 처리 중 Redis 재시작 (AOF) ===")
    base = cb_count()
    publish(3)
    holder = wait(lambda: pel()[0][0] if pel() else None, 15)
    time.sleep(2)
    log(f"PEL = {pel()}  ({holder}가 분석 중)")
    sh("docker", "restart", "chaos-redis")
    log("docker restart chaos-redis")
    wait(lambda: r.ping() if _safe_ping() else False, 30)
    groups = [g["name"].decode() for g in r.xinfo_groups(STREAM)] if r.exists(STREAM) else []
    log(f"Redis 복귀 → 스트림 길이 {r.xlen(STREAM)}, groups = {groups}, PEL = {pel()} (AOF로 보존)")
    ok = watch_pel_changes(lambda: len(callbacks(base)) >= 1 and not pel(), IDLE_S + 60)
    time.sleep(5)  # 중복 콜백이 뒤늦게 오는지 잠시 더 관찰
    cbs = callbacks(base)
    log(f"콜백 = {[c[1:] for c in cbs]}  PEL = {pel()}  DLQ = {r.xlen(DLQ)}")
    errs = sh("docker", "logs", f"chaos-{holder}").count("리스너 루프 오류")
    log(f"{holder} 로그의 '리스너 루프 오류' {errs}건 (재접속 경로 통과 여부)")
    return ok and groups == [GROUP] and all(c[1:] == ("3", "COMPLETED") for c in cbs) and not pel(), len(cbs)


def _safe_ping():
    try:
        return r.ping()
    except redis.exceptions.RedisError:
        return False


if __name__ == "__main__":
    setup()
    s1, recovery = scenario_1_docker_kill()
    s2 = scenario_2_repeated_crash()
    s3, cb_n = scenario_3_redis_restart()
    print("\n=== 결과 ===")
    print(f"1. docker kill 회수:      {'PASS' if s1 else 'FAIL'}  (복구 {recovery:.1f}s)" if recovery else "1. FAIL")
    print(f"2. 반복 크래시 → DLQ:     {'PASS' if s2 else 'FAIL'}")
    print(f"3. Redis 재시작 PEL 보존: {'PASS' if s3 else 'FAIL'}  (콜백 {cb_n}건{' — 중복' if cb_n > 1 else ''})")
    with open(os.path.join(HERE, "data", "timeline.txt"), "w") as f:
        f.write("\n".join(timeline) + "\n")
    sys.exit(0 if s1 and s2 and s3 else 1)
