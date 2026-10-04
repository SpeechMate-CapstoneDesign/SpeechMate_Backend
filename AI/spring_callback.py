# spring_callback.py
import requests
import os
import time
import logging

log = logging.getLogger(__name__)

SPRING_CALLBACK_URL = os.getenv("SPRING_CALLBACK_URL")
CALLBACK_SECRET = os.getenv("CALLBACK_SECRET", "")  # Spring이 같은 값으로 검증 (.env 공유)

_RETRY_DELAYS = [5, 15, 45]


def send_callback_to_spring(speech_id: int, analysis_result: dict, status: str, job_token: str = None) -> bool:
    payload = {
        "speechId": speech_id,
        "response": None if status == "FAILED" else analysis_result,
        "jobToken": job_token,  # Spring은 현재 시도 토큰과 다르면 이 콜백을 무시한다
    }

    for attempt, delay in enumerate(_RETRY_DELAYS, 1):
        try:
            response = requests.post(SPRING_CALLBACK_URL, json=payload, timeout=60,
                                     headers={"X-Callback-Secret": CALLBACK_SECRET})
            if 200 <= response.status_code < 300:
                log.info(f"Spring 콜백 성공 (speechId={speech_id}, attempt={attempt})")
                return True
            log.warning(
                f"Spring 콜백 비정상 응답 (speechId={speech_id}, attempt={attempt}) - "
                f"status={response.status_code}, body={response.text}"
            )
        except requests.exceptions.RequestException as e:
            log.error(f"Spring 콜백 요청 실패 (speechId={speech_id}, attempt={attempt}): {e}")

        if attempt < len(_RETRY_DELAYS):
            time.sleep(delay)

    log.critical(
        f"Spring 콜백 {len(_RETRY_DELAYS)}회 재시도 모두 실패 (speechId={speech_id}). "
        f"호출부가 ACK를 보류해 재처리하거나, Spring 타임아웃 스케줄러가 FAILED 처리."
    )
    return False