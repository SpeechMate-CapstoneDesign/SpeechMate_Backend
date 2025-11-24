import time
import logging
import os
import boto3
import os.path
from typing import Dict, Any, List  # 타입 힌트 추가

log = logging.getLogger(__name__)

# --- Configuration ---
S3_BUCKET_NAME = os.getenv("S3_BUCKET_NAME")
LOCAL_TEMP_DIR = "/tmp"


# --- Analysis Placeholder ---
def do_analysis(local_file_path: str) -> Dict[str, Any]:
    """
    [TODO: Real Analysis] 비언어적 분석 로직이 들어갈 함수.
    현재는 10초 대기 후 더미 데이터를 반환합니다.
    """

    # 1. 🕒 분석 시뮬레이션
    # 이 부분이 실제 Mediapipe 로직으로 대체되어야 합니다.
    log.warning("임시 분석 (10초 대기)... 실제 분석 코드로 교체 필요.")
    time.sleep(10)

    # 2. 📊 최종 결과 구조 (Spring DTO에 맞춰야 함)
    statistics_result = {
        "totalFloorEvents": 2,
        "totalCeilingEvents": 0,
        "totalLeftEvents": 1,
        "totalRightEvents": 0,
        "totalLipBiteEvents": 3,
        "totalHandNearFaceEvents": 0,
        "totalSlantEvents": 1,
        "totalRigidEvents": 0,
        "totalBlinkEvents": 22,

        "totalArmsCrossedEvents": 1,  # 팔짱 끼기 1회
        "totalHandsBehindBackEvents": 0,  # 뒷짐 0회
        "totalHandsRubbingEvents": 2,  # 손 비비기 2회
        "totalFigLeafPoseEvents": 0,  # 무화과 잎 자세 0회
    }

    log_result: List[Dict[str, str]] = [
        {"timestamp": "00:00:12", "event": "입술 깨물기 감지 (총: 1회)"},
        {"timestamp": "00:00:15", "event": "비스듬한 자세 감지 (총: 1회)"},
        {"timestamp": "00:00:20", "event": "바닥 보기 (총: 1회)"}
    ]

    log.info(f"[분석 완료] 임시 분석 결과 생성됨.")

    return {
        "statistics": statistics_result,
        "analysisLog": log_result
    }


# ------------------------------------------------------------------

def run_analysis(s3_key: str) -> Dict[str, Any]:
    """
    [Main Runner] S3 Key를 받아 파일을 다운로드하고 분석을 실행합니다.
    """
    # 1. 임시 디렉토리 생성 보장 (FileNotFoundError 방지)
    os.makedirs(LOCAL_TEMP_DIR, exist_ok=True)

    local_file_name = os.path.basename(s3_key)
    local_path = os.path.join(LOCAL_TEMP_DIR, local_file_name)  # /tmp/filename.mp4

    log.info(f"[S3 다운로드 시작] Key={s3_key}")

    # Boto3 클라이언트 초기화
    # [주의] EC2 IAM Role을 사용하는 것이 가장 안전하므로 access/secret key는 주석 처리합니다.
    s3 = boto3.client(
        "s3",
        aws_access_key_id=os.getenv("S3_ACCESS_KEY"),
        aws_secret_access_key=os.getenv("S3_SECRET_KEY"),
        region_name=os.getenv("S3_REGION", "ap-northeast-2")
    )

    try:
        # 1. S3 스트리밍 다운로드
        obj = s3.get_object(Bucket=S3_BUCKET_NAME, Key=s3_key)
        body = obj["Body"]

        with open(local_path, "wb") as f:
            # 5MB 단위로 청크를 읽어 파일에 씁니다. (대용량 파일에 효율적)
            for chunk in iter(lambda: body.read(1024 * 1024 * 5), b""):
                f.write(chunk)

        log.info(f"✅ S3 다운로드 성공: {local_path}")

        # 2. 분석 수행 (do_analysis 함수 호출)
        result = do_analysis(local_path)

        return result

    except Exception as e:
        # 오류 발생 시 로그를 남기고 예외를 다시 발생시켜 main.py가 FAILED 콜백을 보내도록 유도
        log.error(f"❌ 분석 실행 실패: {e}", exc_info=True)
        raise  # main.py의 process_stream_message가 이 예외를 catch함

    finally:
        # 3. 임시 파일 정리 (try/except/finally 패턴 필수)
        if os.path.exists(local_path):
            os.remove(local_path)
            log.info(f"[정리] 임시 파일 삭제 완료: {local_path}")