# 비언어 분석 파이프라인 장애 주입 실험

Spring → Redis Streams → Python 워커 → 콜백 경로에서 **워커가 죽거나 Redis가 재시작돼도 작업이 유실·중복되지 않는가**를
컨테이너 단위로 직접 주입해 확인한다. 워커 코드는 운영 원본(`AI/main.py`, `AI/spring_callback.py`)을 그대로 마운트하고,
분석 함수만 "N초 걸리는 작업 / 특정 speechId에서 프로세스 크래시"로 바꾼다(`worker/nonverbal_analysis.py`).

## 실행

```bash
cd infra/chaos
docker compose up -d --build          # Redis(AOF) + 워커 2대 + 콜백 sink + exporter/Prometheus/Grafana(3002)
pip install redis && python chaos-test.py
docker compose down -v
```

기본값: 분석 20초, reclaim idle 30초(운영은 10분), 최대 전달 3회. 대시보드는 http://localhost:3002 (운영과 같은 프로비저닝).

## 시나리오와 결과 (2026-09-27)

| # | 주입 | 기대 | 결과 |
|---|---|---|---|
| 1 | 처리 중인 워커 컨테이너 `docker kill` | 다른 워커가 PEL 회수 → 완료, 유실 없음 | PASS. kill 후 32.3초에 worker-b가 회수(`idle 30s + 폴링`), 52.2초에 COMPLETED 콜백. DLQ 0 |
| 2 | 같은 작업이 워커를 반복 크래시(`os._exit`) | 무한 재처리 대신 3회 후 DLQ + FAILED 콜백 | PASS. worker-b → worker-a → worker-a 순으로 3번 죽고(컨테이너 재시작 a+2, b+1) DLQ `max-deliveries-exceeded`, FAILED 콜백 1건 |
| 3 | 처리 중 `docker restart redis` (AOF) | group·PEL 보존, 작업 완료, 콜백 중복 없음 | PASS. 재시작 직후 group 1개·PEL 1건 그대로, worker-a가 완료 후 ACK, COMPLETED 콜백 정확히 1건, 재접속 오류 로그 0건 |

타임라인 전체는 실행 후 `data/timeline.txt`에 남는다.

## 읽는 법

- 시나리오 1의 복구 시간은 `idle 기준 + 폴링 주기(≤5s) + 분석 시간`이다. 운영 idle 10분은 "살아 있는 워커의 느린 작업을 뺏지 않기" 위한 값이라, 워커 사망 시 최대 10분 지연은 설계상 감수한 것.
- 시나리오 2에서 restart 정책으로 되살아난 워커가 같은 메시지를 다시 집어 또 죽는 루프를 **전달 횟수 상한**이 끊는다. DLQ에는 원본 payload와 사유가 남아 재처리 가능.
- 시나리오 3은 `--appendonly yes`가 없으면 group·PEL이 사라져 시나리오 1의 회수 자체가 불가능해진다(운영 compose에 AOF를 넣은 이유).
