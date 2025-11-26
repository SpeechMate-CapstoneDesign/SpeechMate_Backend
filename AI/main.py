import uvicorn
from fastapi import FastAPI
import redis
import threading
import json
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
CONSUMER_GROUP = "analysis-group"  # 소비자 그룹 (Python 서버 그룹)
CONSUMER_NAME = "analysis-consumer-1"  # 현재 실행된 Python 서버의 이름

# --- FastAPI 앱 초기화 ---
app = FastAPI()


def process_stream_message(job_id, job_data):
    """
    메인 작업 처리 로직
    """
    try:
        # 1. Spring이 보낸 'job' 필드를 JSON으로 파싱
        job_body = job_data[b'job'].decode('utf-8')
        job = json.loads(job_body)

        speech_id = job['speechId']
        s3_key = job['s3FileKey']

        log.info(f"[작업 시작] (Job ID: {job_id}) Speech ID: {speech_id}, S3 Key: {s3_key}")

        # 2. [수정 반영] run_analysis 함수를 호출하여 실제 분석 수행
        #    (run_analysis 내부의 5초 대기가 실행됨)
        analysis_result = run_analysis(s3_key, speech_id)

        # 3. Spring Callback API 호출
        log.info(f"Spring으로 콜백 전송 (Speech ID: {speech_id})...")
        send_callback_to_spring(speech_id, analysis_result, "COMPLETED")

        # 4. [중요] 작업 완료 ACK 전송
        return True  # (ACK 전송을 위해 True 반환)

    except Exception as e:
        log.error(f"[작업 실패] (Job ID: {job_id}) 오류: {e}", exc_info=True)
        # (실패 시 Spring에 'FAILED' 콜백 전송)
        send_callback_to_spring(speech_id, None, "FAILED")
        return True  # (실패했더라도, 재시도하지 않으려면 ACK 전송)


def redis_stream_listener():
    """
    Redis Stream을 구독하고 메시지를 처리하는 백그라운드 스레드 함수
    """
    log.info("Redis Stream 리스너 시작. (Host: %s, Stream: %s)", REDIS_HOST, STREAM_KEY)

    # 1. Redis 연결
    r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, password=REDIS_PASSWORD, decode_responses=False)

    # 2. 소비자 그룹 생성 (이미 존재하면 무시됨)
    try:
        r.ping()
        log.info("Redis 연결 및 인증 성공.")

        r.xgroup_create(STREAM_KEY, CONSUMER_GROUP, id='0', mkstream=True)
        log.info("Redis 소비자 그룹 '%s' 생성 완료.", CONSUMER_GROUP)
    except redis.exceptions.ResponseError as e:
        if "name already exists" in str(e):
            log.info("Redis 소비자 그룹 '%s'이(가) 이미 존재합니다.", CONSUMER_GROUP)
        else:
            log.error("Redis 그룹 생성 실패: %s", e)
            return
    except Exception as e:
        log.error(f"Redis 리스너 초기화 실패! 😥 연결/인증 오류: {e}")
        time.sleep(5)
        return


    # 3. 무한 루프로 새 작업 감시
    while True:
        try:
            # 5초마다 새 작업 1개씩 확인 ('>'는 아직 처리 안 된 새 메시지 의미)
            # block=5000 (5초 대기)
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

            # 4. 메인 작업 로직 호출
            if process_stream_message(job_id.decode('utf-8'), job_data):
                # 5. 작업 완료 ACK
                r.xack(STREAM_KEY, CONSUMER_GROUP, job_id)

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