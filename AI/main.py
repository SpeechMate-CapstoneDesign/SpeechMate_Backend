import uvicorn
from fastapi import FastAPI
import redis
import threading
import json
import socket
import time
import os
import logging

# --- (신규) 작업 모듈 임포트 ---
from nonverbal_analysis import run_analysis
from spring_callback import send_callback_to_spring

# --- 로깅 설정 ---
logging.basicConfig(level=logging.INFO)
log = logging.getLogger(__name__)

# --- 설정 (Spring과 일치해야 함) ---
REDIS_HOST = os.getenv("REDIS_HOST")
REDIS_PORT = int(os.getenv("REDIS_PORT", 6379))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD")
STREAM_KEY = "nonverbal-analysis-jobs"  # Spring이 발행하는 Stream 키
DLQ_STREAM_KEY = "nonverbal-analysis-dlq"  # 처리 불능 메시지 보관용 Dead Letter Queue
CONSUMER_GROUP = "analysis-group"  # 소비자 그룹 (Python 서버 그룹)
CONSUMER_NAME = f"analysis-consumer-{socket.gethostname()}"  # 워커별 고유해야 PEL 소유자 추적이 가능
MAX_DELIVERIES = 3  # 이 횟수만큼 전달돼도 처리 못 한 메시지는 DLQ로 이동
RECLAIM_IDLE_MS = int(os.getenv("RECLAIM_IDLE_MS", 10 * 60 * 1000))  # 죽은 워커의 pending 회수 기준 (기본 10분)

# --- FastAPI 앱 초기화 ---
app = FastAPI()


def move_to_dlq(r, job_id, job_data, reason):
    """원본을 DLQ 스트림에 남기고 원본 스트림에서는 ACK 처리한다."""
    entry = dict(job_data)
    entry[b'origin_id'] = job_id
    entry[b'reason'] = reason
    r.xadd(DLQ_STREAM_KEY, entry, maxlen=1000, approximate=True)
    r.xack(STREAM_KEY, CONSUMER_GROUP, job_id)
    log.error(f"[DLQ 이동] (Job ID: {job_id}) 사유: {reason}")


def process_stream_message(r, job_id, job_data):
    """
    메인 작업 처리 로직. True 반환 시 호출부에서 ACK.
    """
    # 1. 파싱은 별도로 시도 — 파싱 불능(포이즌) 메시지는 재시도 의미가 없으니 즉시 DLQ
    try:
        job = json.loads(job_data[b'job'].decode('utf-8'))
        speech_id = job['speechId']
        s3_key = job['s3FileKey']
    except Exception as e:
        log.error(f"[작업 파싱 실패] (Job ID: {job_id}) 오류: {e}", exc_info=True)
        move_to_dlq(r, job_id, job_data, "parse-error")
        return False  # move_to_dlq에서 이미 ACK 완료

    log.info(f"[작업 시작] (Job ID: {job_id}) Speech ID: {speech_id}, S3 Key: {s3_key}")

    try:
        # 2. run_analysis 함수를 호출하여 실제 분석 수행
        analysis_result = run_analysis(s3_key, speech_id)

        # 3. Spring Callback API 호출
        log.info(f"Spring으로 콜백 전송 (Speech ID: {speech_id})...")
        send_callback_to_spring(speech_id, analysis_result, "COMPLETED")
    except Exception as e:
        log.error(f"[작업 실패] (Job ID: {job_id}) Speech ID: {speech_id} 오류: {e}", exc_info=True)
        # 분석 실패는 재시도해도 같은 결과일 가능성이 높음 → FAILED 콜백 후 ACK
        # (Spring 쪽에서 FAILED 상태는 사용자가 재요청 가능)
        send_callback_to_spring(speech_id, None, "FAILED")

    return True


def reclaim_pending(r):
    """
    죽은 워커가 남긴 pending(PEL) 메시지를 회수해 재처리한다.
    MAX_DELIVERIES 이상 전달됐던 메시지는 반복 크래시 유발로 보고 DLQ로 보낸다.
    """
    pending = r.xpending_range(
        STREAM_KEY, CONSUMER_GROUP,
        min='-', max='+', count=10, idle=RECLAIM_IDLE_MS
    )
    for p in pending:
        claimed = r.xclaim(STREAM_KEY, CONSUMER_GROUP, CONSUMER_NAME,
                           RECLAIM_IDLE_MS, [p['message_id']])
        for job_id, job_data in claimed:
            if job_data is None:
                # 본문이 trim으로 이미 사라진 메시지 — ACK만 하고 정리
                r.xack(STREAM_KEY, CONSUMER_GROUP, job_id)
                continue

            if p['times_delivered'] >= MAX_DELIVERIES:
                move_to_dlq(r, job_id, job_data, "max-deliveries-exceeded")
                # 상태가 IN_PROGRESS로 남지 않도록 FAILED 콜백 시도
                try:
                    job = json.loads(job_data[b'job'].decode('utf-8'))
                    send_callback_to_spring(job['speechId'], None, "FAILED")
                except Exception:
                    pass  # 콜백 실패 시 Spring 타임아웃 스케줄러가 정리
                continue

            log.warning(f"[pending 회수] (Job ID: {job_id}) "
                        f"{p['times_delivered']}번째 전달분 재처리 시도")
            if process_stream_message(r, job_id, job_data):
                r.xack(STREAM_KEY, CONSUMER_GROUP, job_id)


def ensure_group(r):
    """소비자 그룹 생성 (이미 존재하면 무시)"""
    try:
        r.xgroup_create(STREAM_KEY, CONSUMER_GROUP, id='0', mkstream=True)
        log.info("Redis 소비자 그룹 '%s' 생성 완료.", CONSUMER_GROUP)
    except redis.exceptions.ResponseError as e:
        if "already exists" not in str(e):
            raise
        log.info("Redis 소비자 그룹 '%s'이(가) 이미 존재합니다.", CONSUMER_GROUP)


def redis_stream_listener():
    """
    Redis Stream을 구독하고 메시지를 처리하는 백그라운드 스레드 함수
    """
    log.info("Redis Stream 리스너 시작. (Host: %s, Stream: %s, Consumer: %s)",
             REDIS_HOST, STREAM_KEY, CONSUMER_NAME)

    r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, password=REDIS_PASSWORD, decode_responses=False)

    # 1. 초기화 — 실패해도 스레드가 죽지 않고 성공할 때까지 재시도
    #    (스레드가 죽으면 FastAPI는 살아 있는데 소비만 멈추는 '조용한 장애'가 됨)
    while True:
        try:
            r.ping()
            log.info("Redis 연결 및 인증 성공.")
            ensure_group(r)
            break
        except Exception as e:
            log.error(f"Redis 리스너 초기화 실패, 5초 후 재시도: {e}")
            time.sleep(5)

    # 2. 무한 루프로 작업 감시 (pending 회수 → 새 메시지 소비)
    while True:
        try:
            reclaim_pending(r)

            # 5초마다 새 작업 1개씩 확인 ('>'는 아직 처리 안 된 새 메시지 의미)
            messages = r.xreadgroup(
                CONSUMER_GROUP,
                CONSUMER_NAME,
                {STREAM_KEY: '>'},
                count=1,
                block=5000
            )

            if not messages:
                continue

            stream_key, msgs = messages[0]
            job_id, job_data = msgs[0]

            if process_stream_message(r, job_id, job_data):
                r.xack(STREAM_KEY, CONSUMER_GROUP, job_id)

        except redis.exceptions.ResponseError as e:
            if "NOGROUP" in str(e):
                # 페일오버/데이터 유실로 스트림·그룹이 사라진 경우 재생성 후 계속
                log.warning("소비자 그룹이 사라져 재생성합니다.")
                try:
                    ensure_group(r)
                except Exception as ge:
                    log.error(f"그룹 재생성 실패: {ge}")
                    time.sleep(5)
            else:
                log.error(f"Redis 리스너 루프 오류: {e}", exc_info=True)
                time.sleep(5)
        except Exception as e:
            log.error(f"Redis 리스너 루프 오류: {e}")
            time.sleep(5)


# --- FastAPI 앱 설정 ---

@app.on_event("startup")
def startup_event():
    # FastAPI 서버 시작 시, Redis 리스너를 별도 스레드로 실행
    listener_thread = threading.Thread(target=redis_stream_listener, daemon=True)
    listener_thread.start()


@app.get("/health")
def health_check():
    return {"status": "ok"}


# --- 서버 실행 (로컬 테스트용) ---
if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
