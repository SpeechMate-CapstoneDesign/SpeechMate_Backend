# 실험용 워커 이미지: 운영 이미지(AI/Dockerfile)에서 mediapipe/opencv만 뺀 것.
# 소스는 compose에서 볼륨으로 마운트한다 (AI/main.py, AI/spring_callback.py 원본 + 분석 stub).
FROM python:3.11-slim-bullseye
WORKDIR /app
RUN pip install --no-cache-dir redis requests fastapi "uvicorn[standard]"
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
