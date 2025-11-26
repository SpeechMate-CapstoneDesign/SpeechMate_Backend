# spring_callback.py
import requests
import os
import logging

log = logging.getLogger(__name__)

# Spring 서버의 콜백 URL (환경 변수 또는 설정 파일로 관리)
SPRING_CALLBACK_URL = os.getenv(
    "SPRING_CALLBACK_URL"
)


def send_callback_to_spring(speech_id: int, analysis_result: dict, status: str):
    """
    분석 결과를 Spring 서버의 콜백 API로 전송합니다.
    """

    # 1. Spring DTO 형식에 맞는 페이로드(Payload) 구성
    # (NonVerbalAnalysisCallbackRequest)
    payload = {
        "speechId": speech_id,
        "response": analysis_result  # (성공 시)
    }

    # 2. [신규] 실패 시 'response'를 null로 설정
    if status == "FAILED":
        payload["response"] = None

    try:
        response = requests.post(SPRING_CALLBACK_URL, json=payload, timeout=10)

        if 200 <= response.status_code < 300:
            log.info(f"Spring 콜백 성공 (Speech ID: {speech_id}, Status: {status})")
        else:
            log.warning(
                f"Spring 콜백 실패 (Speech ID: {speech_id}) - "
                f"Status: {response.status_code}, Body: {response.text}"
            )

    except requests.exceptions.RequestException as e:
        log.error(f"Spring 콜백 전송 실패 (Speech ID: {speech_id}) - 오류: {e}")