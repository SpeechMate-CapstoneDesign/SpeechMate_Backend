"""측정용 분석 stub — 운영 nonverbal_analysis.run_analysis 자리에 들어간다.
이 Mac에는 S3 자격증명·영상이 없어 실제 MediaPipe 대신 ANALYSIS_SLEEP초 걸리는 작업으로 대체한다.
큐·스레드·유실 거동은 분석 내용이 아니라 '오래 걸리는 작업'이라는 성질에만 의존한다."""
import logging
import os
import time

log = logging.getLogger(__name__)
SLEEP = float(os.getenv("ANALYSIS_SLEEP", 20))


def run_analysis(s3_key, speech_id):
    log.info("[stub] 분석 시작 speech=%s (%.0fs)", speech_id, SLEEP)
    time.sleep(SLEEP)
    log.info("[stub] 분석 완료 speech=%s", speech_id)
    return {"totalCount": 0, "results": {}}
