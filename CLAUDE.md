# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 명령어

```bash
# 빌드 (테스트 제외)
./gradlew clean build -x test

# 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "com.example.speechmate_backend.speech.SpeechTest"

# 로컬 실행 (MySQL + Redis 먼저 실행 필요)
./gradlew bootRun --args='--spring.profiles.active=local'

# 전체 로컬 인프라 실행 (Spring + MySQL + Redis + Nginx + AI 워커)
docker compose up -d

# 모니터링 스택 실행 (Prometheus + Grafana)
cd infra && docker compose -f docker-compose.monitoring.yml up -d
```

기본 프로파일은 `local`(`src/main/resources/application.yml`에서 설정). `local` 프로파일은 `127.0.0.1:3306/speechmate`와 로컬 Redis(6379)에 연결한다. `dev` 프로파일은 운영 Docker 컨테이너에서 사용한다.

FFmpeg가 로컬에 설치되어 있어야 하며, 경로는 프로파일별로 `ffmpeg.path`에 설정한다(`application-local.yml`은 Windows 경로, `application-dev.yml`은 `/usr/bin/ffmpeg`).

## 아키텍처

### 시스템 개요

두 개의 런타임이 협력한다:
- **Spring Boot 3.3.4 (Java 17)** — 메인 REST API (`src/main/java/com/example/speechmate_backend/`)
- **Python FastAPI 워커** — 비언어적 영상 분석 (`AI/`, 포트 8000)

두 서버 간 통신은 **Redis Streams**를 사용한다. Spring이 `nonverbal-analysis-jobs` 스트림에 작업을 발행하면, Python 워커가 소비하여 분석 완료 후 `/api/callback/speech/non-verbal`로 콜백을 보낸다.

### Spring Boot 패키지 구조

| 패키지 | 역할 |
|---|---|
| `speech` | 핵심 도메인: 업로드, STT, AI 분석, 피드 |
| `user` | 유저 엔티티, 로그인/로그아웃, 토큰 재발급 |
| `oauth` | 카카오 OIDC 로그인 (JWKS로 ID 토큰 검증) |
| `s3` | AWS S3 Presigned URL 발급 및 오브젝트 관리 |
| `fcm` | Firebase Cloud Messaging 푸시 알림 |
| `config.security` | JWT 필터, Stateless Spring Security 설정 |
| `common` | `ApiResponse<T>` 래퍼, `BaseEntity`, `@DistributedLock` AOP, 전역 예외 핸들러 |

### 스피치 분석 흐름

1. **업로드**: `POST /api/speech/presignedWithS3` → Presigned URL 발급 → 클라이언트가 S3에 직접 파일 업로드 → `POST /api/speech/s3-callback`으로 `Speech` 엔티티 등록
2. **STT(언어적 분석)**: `POST /api/speech/rtzrstt/{speechId}` — S3에서 파일 다운로드, FFmpeg로 MP3 변환, ReturnZero(vito.ai) 제출, 완료 폴링, 원본 JSON 저장 + 침묵 구간·간투어·어절/음절 수 추출 → `VerbalAnalysisResult` 저장
3. **텍스트 분석**: `POST /api/speech/analyze/{speechId}` — Spring AI로 GPT-4o-mini에 대본 전송, 구조화된 `GptResponse`(요약, 키워드, 개선점, 피드백, 예상 질문, 반복 단어) 파싱 → `AnalysisResult` 저장
4. **비언어적 분석**: `POST /api/speech/nonverbal/{speechId}` — Redis Stream에 작업 발행 → Python 워커가 S3에서 영상 다운로드 후 분석 → 콜백 수신 → `NonVerbalAnalysisResult` 저장. 상태는 `AnalysisStatus` 열거형으로 추적(`NOT_STARTED` → `IN_PROGRESS` → `COMPLETED/FAILED`)

### 주요 패턴

**`@DistributedLock`** — Redisson 기반 분산 락 어노테이션. STT 중복 처리 방지에 사용. `DistributedLockAop`가 `AopForTransaction`을 통해 별도 트랜잭션 안에서 메서드를 실행하여 커밋 전 락 해제를 방지한다.

**`ApiResponse<T>`** — 모든 REST 엔드포인트의 공통 응답 래퍼. 커스텀 예외는 `SmateException`을 상속하며 `ErrorCodeIfs`로 에러 코드를 정의한다. `GlobalExceptionHandler`에서 일괄 처리.

**커서 기반 페이지네이션** — 피드·목록 API는 `lastSpeechId` + `limit` 커서 방식 사용(offset/`Page<>` 미사용). 커스텀 쿼리는 `SpeechCustomRepositoryImpl`(QueryDSL)에 위치.

**JWT 인증** — `JwtFilter`가 Spring Security 필터 앞에 동작. Access + Refresh 토큰 발급; Refresh 토큰은 Redis에 저장, 로그아웃 시 블랙리스트 처리. 재발급 엔드포인트: `/api/auth/reissue`.

**카카오 OIDC** — 서버 사이드 리다이렉트 없이 프론트가 ID 토큰을 직접 전달. `https://kauth.kakao.com`의 JWKS로 토큰 검증.

### 인프라

운영 환경은 EC2에서 GitHub Actions(`.github/workflows/deploy.yml`)으로 **블루-그린 배포**를 수행한다. `dev` 브랜치 푸시 시 빌드 → Docker 이미지 푸시 → SSH 배포 → Nginx가 `capstone-server-blue`(8080)와 `capstone-server-green`(8081) 사이를 전환한다. 헬스체크 실패 시 자동 롤백.

모니터링: Prometheus가 `/actuator/prometheus`를 스크랩, Grafana 대시보드는 포트 3000. Redis 메트릭은 `redis_exporter`로 수집.

### 외부 서비스 의존성

| 서비스 | 용도 | 설정 키 |
|---|---|---|
| AWS S3 | 파일 저장소 | `cloud.aws.*` |
| ReturnZero (vito.ai) | 한국어 STT | `returnzero.*` |
| OpenAI (GPT-4o-mini + Whisper) | 텍스트 분석 및 STT 폴백 | `spring.ai.openai.*` |
| 카카오 | OIDC 로그인 | `oauth.kakao.*` |
| Firebase | FCM 푸시 알림 | `firebase.key` |
| Redis (Redisson) | 분산 락, 토큰 저장소, 작업 스트림 | `spring.data.redis.*` |
| MySQL | 기본 데이터 저장소 | `spring.datasource.*` |
