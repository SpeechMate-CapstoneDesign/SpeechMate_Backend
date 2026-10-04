# 측정용 워커: 운영 main.py(experiment 브랜치 = Streams 루프 + /analyze) + stub 분석. 컨텍스트는 저장소 루트.
FROM python:3.11-slim
RUN pip install --no-cache-dir "redis>=5,<9" fastapi "uvicorn[standard]" requests
WORKDIR /app
COPY AI/spring_callback.py AI/redis_conn.py ./
COPY infra/measure/worker/main.py infra/measure/worker/nonverbal_analysis.py ./
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
