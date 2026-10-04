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
from redis_conn import make_redis

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
XREAD_BLOCK_MS = 5000  # 새 메시지 대기 시간 = 회수·종료 신호 확인 주기
RECLAIM_IDLE_MS = int(os.getenv("RECLAIM_IDLE_MS", 10 * 60 * 1000))  # 죽은 워커의 pending 회수 기준 (기본 10분)
SHUTDOWN_GRACE_S = int(os.getenv("SHUTDOWN_GRACE_S", 110))  # SIGTERM 후 진행 중 작업을 기다리는 시간 (compose stop_grace_period보다 짧게)
stop_event = threading.Event()  # SIGTERM → 새 메시지는 그만 읽고 진행 중 작업만 끝낸다
listener_thread = None

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
    False = ACK하지 말 것 (이미 DLQ로 보내 ACK했거나, 결과를 Spring에 못 넘겨 재처리가 필요한 경우).
    """
    # 1. 파싱은 별도로 시도 — 파싱 불능(포이즌) 메시지는 재시도 의미가 없으니 즉시 DLQ
    try:
        job = json.loads(job_data[b'job'].decode('utf-8'))
        speech_id = job['speechId']
        s3_key = job['s3FileKey']
        job_token = job.get('jobToken')  # Spring이 이 시도의 콜백인지 판별하는 값 (구버전 메시지는 없음)
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
        if not send_callback_to_spring(speech_id, analysis_result, "COMPLETED", job_token):
            # 몇 분짜리 분석 결과를 버리지 않는다: ACK하지 않고 PEL에 남겨 idle 뒤 회수·재분석되게 한다.
            # (MAX_DELIVERIES 넘으면 DLQ + FAILED 콜백으로 종결)
            log.error(f"[콜백 실패, ACK 보류] (Job ID: {job_id}) Speech ID: {speech_id} — 회수 후 재처리 예정")
            return False
    except Exception as e:
        log.error(f"[작업 실패] (Job ID: {job_id}) Speech ID: {speech_id} 오류: {e}", exc_info=True)
        # 분석 실패는 재시도해도 같은 결과일 가능성이 높음 → FAILED 콜백 후 ACK
        # (Spring 쪽에서 FAILED 상태는 사용자가 재요청 가능. FAILED 콜백까지 실패하면 타임아웃 스케줄러가 정리)
        send_callback_to_spring(speech_id, None, "FAILED", job_token)

    return True


def reclaim_pending(r):
    """
    죽은 워커가 남긴 pending(PEL) 메시지를 회수해 재처리한다.
    MAX_DELIVERIES 이상 전달됐던 메시지는 반복 크래시 유발로 보고 DLQ로 보낸다.
    """
    # XAUTOCLAIM = "idle 넘은 pending을 찾아서(XPENDING) 내 소유로(XCLAIM)"를 한 명령으로. min-idle 검사가 원자적이라
    # 두 워커가 동시에 회수해도 한쪽만 가져간다. Redis 7은 trim으로 본문이 사라진 항목을 PEL에서 알아서 치우고
    # 세 번째 반환값으로 알려준다 (6.2는 (id, None)으로 섞여 오므로 아래 None 처리 유지).
    _next, claimed = r.xautoclaim(STREAM_KEY, CONSUMER_GROUP, CONSUMER_NAME,
                                  RECLAIM_IDLE_MS, start_id='0-0', count=10)[:2]
    for job_id, job_data in claimed:
        if job_data is None:
            r.xack(STREAM_KEY, CONSUMER_GROUP, job_id)
            continue

        # 전달 횟수는 XAUTOCLAIM 응답에 없어 따로 본다. 이번 회수로 이미 +1 된 값이라 '초과'로 비교.
        delivered = r.xpending_range(STREAM_KEY, CONSUMER_GROUP, job_id, job_id, 1)[0]['times_delivered']
        if delivered > MAX_DELIVERIES:
            move_to_dlq(r, job_id, job_data, "max-deliveries-exceeded")
            # 상태가 IN_PROGRESS로 남지 않도록 FAILED 콜백 시도
            try:
                job = json.loads(job_data[b'job'].decode('utf-8'))
                send_callback_to_spring(job['speechId'], None, "FAILED", job.get('jobToken'))
            except Exception:
                pass  # 콜백 실패 시 Spring 타임아웃 스케줄러가 정리
            continue

        log.warning(f"[pending 회수] (Job ID: {job_id}) {delivered}번째 전달분 재처리 시도")
        if process_stream_message(r, job_id, job_data):
            r.xack(STREAM_KEY, CONSUMER_GROUP, job_id)


def deregister_consumer(r):
    """소비자 이름이 컨테이너 hostname이라 배포마다 새 이름이 생긴다. 안 지우면 XINFO CONSUMERS와
    Grafana idle 패널이 죽은 이름으로 채워진다. pending이 남아 있으면 지우지 않는다 (DELCONSUMER는 pending을 버림)."""
    try:
        if r.xpending_range(STREAM_KEY, CONSUMER_GROUP, '-', '+', 1, consumername=CONSUMER_NAME):
            log.warning("소비자 %s에 pending이 남아 있어 이름을 유지합니다 (회수 경로로 복구됨)", CONSUMER_NAME)
            return
        r.xgroup_delconsumer(STREAM_KEY, CONSUMER_GROUP, CONSUMER_NAME)
        log.info("소비자 %s 등록 해제", CONSUMER_NAME)
    except Exception as e:
        log.warning("소비자 등록 해제 실패 (무시): %s", e)


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
    log.info("Redis Stream 리스너 시작. (Mode: %s, Host: %s, Stream: %s, Consumer: %s)",
             os.getenv("REDIS_MODE", "standalone"), REDIS_HOST, STREAM_KEY, CONSUMER_NAME)

    # socket_timeout은 XREADGROUP block(5s)보다 충분히 길어야 한다.
    # redis-py 8부터 기본 socket_timeout이 5초라 5초 블로킹 읽기와 경쟁해 TimeoutError가 나고,
    # 기본 재시도(3회+지수 백오프)까지 겹치면 호출 한 번이 최대 60초 루프 밖에서 멈춘다
    # → 그동안 pending 회수도 종료 신호 처리도 안 됨 (장애 주입 실험에서 회수 90초로 발견).
    # 토폴로지(standalone/sentinel/cluster)는 REDIS_MODE로 고른다 (redis_conn.py).
    r = make_redis(socket_timeout=XREAD_BLOCK_MS / 1000 + 10)

    # 1. 초기화 — 실패해도 스레드가 죽지 않고 성공할 때까지 재시도
    #    (스레드가 죽으면 FastAPI는 살아 있는데 소비만 멈추는 '조용한 장애'가 됨)
    while not stop_event.is_set():
        try:
            r.ping()
            log.info("Redis 연결 및 인증 성공.")
            ensure_group(r)
            break
        except Exception as e:
            log.error(f"Redis 리스너 초기화 실패, 5초 후 재시도: {e}")
            time.sleep(5)

    # 2. 작업 감시 루프 (pending 회수 → 새 메시지 소비). stop_event가 서면 현재 작업까지만 하고 빠져나간다
    while not stop_event.is_set():
        try:
            reclaim_pending(r)

            # 5초마다 새 작업 1개씩 확인 ('>'는 아직 처리 안 된 새 메시지 의미)
            messages = r.xreadgroup(
                CONSUMER_GROUP,
                CONSUMER_NAME,
                {STREAM_KEY: '>'},
                count=1,
                block=XREAD_BLOCK_MS
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
                log.error(f"Redis 리스너 루프 오류: {type(e).__name__}: {e}", exc_info=True)
                time.sleep(5)
        except Exception as e:
            # 예외 클래스를 같이 남긴다 — 페일오버 중 ConnectionError / MasterNotFoundError / ClusterDownError 를 구분해야
            # "어느 구간에서 무엇 때문에 멈췄는지"를 로그만으로 알 수 있다.
            log.error(f"Redis 리스너 루프 오류: {type(e).__name__}: {e}")
            time.sleep(5)

    deregister_consumer(r)
    log.info("Redis Stream 리스너 종료 (진행 중이던 작업까지 처리 완료).")


# --- FastAPI 앱 설정 ---

@app.on_event("startup")
def startup_event():
    # FastAPI 서버 시작 시, Redis 리스너를 별도 스레드로 실행
    global listener_thread
    listener_thread = threading.Thread(target=redis_stream_listener, daemon=True)
    listener_thread.start()


@app.on_event("shutdown")
def shutdown_event():
    # uvicorn이 SIGTERM을 받으면 여기로 온다. 리스너를 멈추고 진행 중 작업이 ACK까지 끝나길 기다린다.
    # 이게 없으면 배포마다 처리 중이던 작업이 PEL에 남아 idle 기준(10분) 뒤에야 회수된다.
    log.info("종료 신호 수신 — 새 작업 수신 중단, 진행 중 작업 대기 (최대 %ds)", SHUTDOWN_GRACE_S)
    stop_event.set()
    if listener_thread is not None:
        listener_thread.join(timeout=SHUTDOWN_GRACE_S)
        if listener_thread.is_alive():
            log.error("진행 중 작업이 %ds 안에 끝나지 않아 강제 종료 — PEL 회수 경로로 복구됨", SHUTDOWN_GRACE_S)


@app.get("/health")
def health_check():
    return {"status": "ok"}


# --- 서버 실행 (로컬 테스트용) ---
if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
