# @Async vs Redis Streams 측정 스택

운영 compose(Spring + MySQL + Redis + ai-worker)와 같은 구성에서 비밀(.env, S3)만 뺀 것. 앱은 test 프로파일(외부 서비스 더미),
워커 분석은 `ANALYSIS_SLEEP`초 stub. 모니터링은 `infra/conf`의 Grafana 대시보드를 그대로 쓴다.
결과·해석은 `speechmate_backend_issue/06-async-vs-streams.md` 7절.

```bash
./gradlew bootJar -x test && cp build/libs/*SNAPSHOT.jar infra/measure/jars/app-streams.jar          # dev
git checkout experiment/async-http && ./gradlew bootJar -x test && cp build/libs/*SNAPSHOT.jar infra/measure/jars/app-async.jar
cd infra/measure
python3 measure.py up streams --workers 2 --sleep 10        # 또는 up async [--pool 4,4,20]
python3 measure.py burst 100                                # 동시 100건 → 지연·상태코드·소진·Prometheus 피크
python3 measure.py kill-spring                              # 10건 투입 중 app kill → 유실/고착/중복 집계
python3 measure.py down
```

Grafana http://127.0.0.1:13000 (anonymous admin), Prometheus http://127.0.0.1:19090, 앱 http://127.0.0.1:18080.
결과 JSON은 `data/`에 남는다. 워커 `main.py`는 `git show experiment/async-http:AI/main.py`로 뽑아 쓴다(Streams 루프 + `/analyze` 둘 다 있음).

주의: `docker kill`은 수동 중지라 `restart: always`가 되살리지 않는다. 스크립트는 kill 직후 start해서 재시작 정책과 같은 다운타임(JVM 부팅 ≈7초)을 만든다.
