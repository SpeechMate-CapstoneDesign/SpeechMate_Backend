"""장애 주입용 분석 stub — 운영의 nonverbal_analysis.run_analysis 자리에 마운트된다.

- 기본: ANALYSIS_SLEEP초 걸리는 작업인 척한다 (그 사이 docker kill / redis restart 주입)
- speechId == CRASH_SPEECH_ID: 처리 도중 프로세스가 통째로 죽는다 (os._exit → 컨테이너 종료 → restart 정책이 되살림)
"""
import logging
import os
import time

log = logging.getLogger(__name__)
SLEEP = float(os.getenv("ANALYSIS_SLEEP", 20))
CRASH_ID = int(os.getenv("CRASH_SPEECH_ID", -1))


def run_analysis(s3_key, speech_id):
    if int(speech_id) == CRASH_ID:
        log.error("[chaos] speech %s 처리 중 프로세스 크래시 (os._exit 137)", speech_id)
        time.sleep(1)
        os._exit(137)
    log.info("[chaos] 분석 시작 speech=%s (%.0fs 소요)", speech_id, SLEEP)
    time.sleep(SLEEP)
    log.info("[chaos] 분석 완료 speech=%s", speech_id)
    return {"chaos": True, "speechId": speech_id}
