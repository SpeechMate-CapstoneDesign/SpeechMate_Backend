import json
import logging
import os
import boto3
import os.path
import cv2
from typing import Dict, Any, List  # 타입 힌트 추가
from gesture_detector_video import GestureDetector


log = logging.getLogger(__name__)

# --- Configuration ---
S3_BUCKET_NAME = os.getenv("S3_BUCKET_NAME")
LOCAL_TEMP_DIR = "/tmp"

class VideoGestureAnalyzer:
    def __init__(self, video_path):
        """비디오 파일 분석기 (서버용 최적화)"""
        self.cap = cv2.VideoCapture(video_path)

        if not self.cap.isOpened():
            raise ValueError(f"Cannot open video: {video_path}")

        # 비디오 정보
        self.fps = int(self.cap.get(cv2.CAP_PROP_FPS)) or 30
        self.total_frames = int(self.cap.get(cv2.CAP_PROP_FRAME_COUNT))

        # GestureDetector 초기화
        self.detector = GestureDetector(fps=self.fps)

        # 제스처 그룹 매핑
        self.behavior_groups = {
            "HEAD": ["고개숙이기", "천장보기", "고개흔들기"],
            "ARMS": ["팔짱끼기", "뒷짐"],
            "HANDS": ["손비비기", "무화과잎자세"],
            "POSTURE": ["비스듬한자세", "경직된차려"],
            "FACE": ["입술깨물기", "눈깜빡임", "머리터치", "이마터치",
                     "코터치", "입술터치", "턱터치", "왼쪽귀터치", "오른쪽귀터치"]
        }

    def analyze(self):
        """비디오 전체 분석 (최적화)"""
        frame_idx = 0
        while frame_idx < self.total_frames:
            ret, frame = self.cap.read()
            if not ret: break

            # 프레임 처리 (시각화 없음)
            timestamp_ms = self.cap.get(cv2.CAP_PROP_POS_MSEC)
            self.detector.process_frame(frame, timestamp_ms)
            frame_idx += 1

        self.cap.release();
        self.detector.close()

    def generate_json(self):
        results = {}
        total_count = 0

        for group_key, behaviors_list in self.behavior_groups.items():
            group_behaviors = []

            for behavior_name in behaviors_list:
                timestamps = self.detector.timeline.get(behavior_name, [])

                if timestamps:
                    # 문자열 그대로 JSON에 넣기
                    group_behaviors.append({
                        "name": behavior_name,
                        "count": len(timestamps),
                        "timestamps": timestamps
                    })

                    total_count += 1

            if group_behaviors:
                results[group_key] = group_behaviors

        return {
            "totalCount": total_count,
            "results": results
        }

    def save_json(self, output_path):
        """JSON 파일 저장"""
        json_data = self.generate_json()

        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(json_data, f, ensure_ascii=False, separators=(',', ':'))

        return json_data


def analyze_video(video_path, speech_id=None, output_path=None):
    """
    비디오 분석 및 JSON 생성 (서버용 함수)

    Args:
        video_path: 비디오 파일 경로
        output_path: JSON 저장 경로 (None이면 자동 생성)

    Returns:
        dict: 분석 결과 JSON
    """
    analyzer = VideoGestureAnalyzer(video_path)
    analyzer.analyze()

    return analyzer.generate_json()


# ------------------------------------------------------------------

def run_analysis(s3_key: str, speech_id: int) -> Dict[str, Any]:
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
        result = analyze_video(local_path, speech_id)

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