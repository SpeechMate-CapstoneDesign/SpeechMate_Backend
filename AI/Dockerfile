# Python AI 워커용 Dockerfile
FROM python:3.11-slim

# 1. 시스템 의존성 설치 (OpenCV/MediaPipe Headless 실행에 필요)
RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# 2. 작업 디렉토리 설정
WORKDIR /app

# 3. 의존성 파일 복사 및 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 4. 소스 코드 복사 (AI 폴더 컨텍스트에서 실행됨)
COPY main.py nonverbal_analysis.py spring_callback.py ./

# 5. Uvicorn 서버 실행 (포트 8000)
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]