"""
워커 장애 주입 테스트 — Redis Streams 재처리/DLQ 로직 실동작 검증.

실행: REDIS_HOST=127.0.0.1 python test_stream_reliability.py   (로컬 Redis 필요, 무거운 분석 모듈은 stub 처리)

시나리오
  A. 처리 중 워커 사망 → 다른 워커가 idle pending을 XCLAIM으로 회수해 재처리, ACK 후 PEL 비어 있음
  B. 같은 메시지로 워커가 3번 죽음 → 무한 재처리 대신 DLQ 이동 + FAILED 콜백
  C. 포이즌 메시지(JSON 깨짐) → 재시도 없이 즉시 DLQ
  D. 분석 로직 자체 실패 → FAILED 콜백 후 ACK(재시도는 사용자 재요청에 맡김)
"""
import json
import os
import sys
import time
import types

os.environ.setdefault("REDIS_HOST", "127.0.0.1")
os.environ["RECLAIM_IDLE_MS"] = "200"  # 테스트에서는 0.2초만 방치돼도 '죽은 워커'로 간주

# 무거운 의존(mediapipe, opencv, Spring HTTP)은 stub — 여기서 검증하는 건 큐 로직뿐
analysis_calls, callbacks = [], []
stub_analysis = types.ModuleType("nonverbal_analysis")
stub_analysis.run_analysis = lambda s3_key, speech_id: analysis_calls.append(speech_id) or {"ok": True}
stub_callback = types.ModuleType("spring_callback")
stub_callback.send_callback_to_spring = lambda speech_id, result, status: callbacks.append((speech_id, status))
sys.modules["nonverbal_analysis"] = stub_analysis
sys.modules["spring_callback"] = stub_callback

import redis  # noqa: E402
import main  # noqa: E402

main.STREAM_KEY = "test:nonverbal-analysis-jobs"
main.DLQ_STREAM_KEY = "test:nonverbal-analysis-dlq"
main.CONSUMER_NAME = "survivor-worker"
GROUP = main.CONSUMER_GROUP

r = redis.Redis(host=os.environ["REDIS_HOST"], port=int(os.getenv("REDIS_PORT", 6379)),
                password=os.getenv("REDIS_PASSWORD"), decode_responses=False)


def reset():
    r.delete(main.STREAM_KEY, main.DLQ_STREAM_KEY)
    main.ensure_group(r)
    analysis_calls.clear()
    callbacks.clear()


def publish(speech_id, body=None):
    body = body or json.dumps({"speechId": speech_id, "s3FileKey": f"videos/{speech_id}.mp4"})
    return r.xadd(main.STREAM_KEY, {"job": body})


def crash_while_processing(consumer):
    """XREADGROUP으로 메시지를 가져간 뒤 ACK 없이 사라진 워커를 흉내 낸다."""
    msgs = r.xreadgroup(GROUP, consumer, {main.STREAM_KEY: ">"}, count=1)
    return msgs[0][1][0][0]


def pending_count():
    return r.xpending(main.STREAM_KEY, GROUP)["pending"]


def test_a_dead_worker_job_is_reclaimed():
    reset()
    publish(1)
    job_id = crash_while_processing("dead-worker")
    assert pending_count() == 1 and analysis_calls == []

    main.reclaim_pending(r)  # 아직 idle 200ms 안 지남 → 회수하면 안 됨
    assert analysis_calls == [], "idle 기준 전에 회수하면 살아 있는 워커의 작업을 뺏는 것"

    time.sleep(0.3)
    main.reclaim_pending(r)
    assert analysis_calls == [1], "죽은 워커의 작업이 재처리돼야 함"
    assert callbacks == [(1, "COMPLETED")]
    assert pending_count() == 0, f"ACK 후 PEL이 비어야 함 (job {job_id})"


def test_b_repeated_crash_goes_to_dlq():
    reset()
    publish(2)
    crash_while_processing("worker-1")
    for i in range(2):  # 2번 더 회수됐다가 또 죽음 → times_delivered = 3
        time.sleep(0.3)
        pend = r.xpending_range(main.STREAM_KEY, GROUP, "-", "+", 1)[0]
        r.xclaim(main.STREAM_KEY, GROUP, f"worker-{i + 2}", 200, [pend["message_id"]])
    assert r.xpending_range(main.STREAM_KEY, GROUP, "-", "+", 1)[0]["times_delivered"] == 3

    time.sleep(0.3)
    main.reclaim_pending(r)
    assert analysis_calls == [], "3회 전달된 메시지는 다시 처리하지 않아야 함"
    assert callbacks == [(2, "FAILED")], "IN_PROGRESS로 남지 않도록 FAILED 콜백"
    assert pending_count() == 0
    dlq = r.xrange(main.DLQ_STREAM_KEY)
    assert len(dlq) == 1 and dlq[0][1][b"reason"] == b"max-deliveries-exceeded"
    assert json.loads(dlq[0][1][b"job"])["speechId"] == 2, "원본 페이로드가 DLQ에 보존돼야 재처리 가능"


def test_c_poison_message_goes_straight_to_dlq():
    reset()
    publish(3, body="{not json")
    job_id, data = r.xreadgroup(GROUP, "worker", {main.STREAM_KEY: ">"}, count=1)[0][1][0]
    assert main.process_stream_message(r, job_id, data) is False
    assert analysis_calls == [] and callbacks == []
    assert pending_count() == 0, "포이즌 메시지는 ACK돼 PEL에서 빠져야 함"
    assert r.xrange(main.DLQ_STREAM_KEY)[0][1][b"reason"] == b"parse-error"


def test_d_analysis_failure_sends_failed_and_acks():
    reset()
    publish(4)
    job_id, data = r.xreadgroup(GROUP, "worker", {main.STREAM_KEY: ">"}, count=1)[0][1][0]
    def boom(*a):
        raise RuntimeError("mediapipe boom")
    main.run_analysis, original = boom, main.run_analysis  # main이 import 시점에 바인딩한 이름을 직접 교체
    try:
        assert main.process_stream_message(r, job_id, data) is True, "실패도 ACK 대상"
    finally:
        main.run_analysis = original
    assert callbacks == [(4, "FAILED")]
    assert r.xlen(main.DLQ_STREAM_KEY) == 0, "분석 실패는 DLQ가 아니라 사용자 재요청 경로"


if __name__ == "__main__":
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for t in tests:
        t()
        print(f"PASS {t.__name__}")
    r.delete(main.STREAM_KEY, main.DLQ_STREAM_KEY)
    print(f"{len(tests)}/{len(tests)} passed")
