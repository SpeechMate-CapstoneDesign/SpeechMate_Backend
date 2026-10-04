package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.speech.AnalysisStatus;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * 비언어 분석 작업을 커밋 "뒤에, 요청 스레드 밖에서" Redis Stream에 발행한다.
 *
 * 왜 afterCommit 안에서 직접 XADD하지 않나: Spring은 afterCommit 콜백을 다 돌린 뒤에야 DB 커넥션을 반납한다.
 * Redis 페일오버 중 XADD가 재시도로 수 초 매달리면 그동안 커넥션을 쥔 요청이 쌓여 HikariCP 풀(10)이 바닥나고,
 * Redis와 무관한 조회까지 커넥션을 기다렸다 (infra/failover/README.md "실제 앱 + k6", kill+5s 덤프에서 커넥션 대기 13 스레드).
 * 그래서 커밋 뒤 이벤트로 받아 executor에 넘기고 즉시 돌아온다 → 요청 스레드는 바로 커넥션을 반납한다.
 * 롤백되면 이벤트가 버려지므로 "DB에 없는 작업이 스트림에만 남는" 문제는 그대로 막힌다.
 *
 * 발행이 끝내 실패하면 상태를 FAILED로 돌려 사용자가 바로 재요청할 수 있게 한다 (예전엔 30분 스케줄러를 기다려야 했다).
 * jobToken이 같을 때만 돌린다 — 그 사이 새 요청이 들어와 토큰이 바뀌었으면 그 요청의 상태를 건드리면 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NonVerbalJobPublisher {

    public static final String STREAM_KEY = "nonverbal-analysis-jobs";
    static final int MAX_STREAM_LENGTH = 10000;

    private final RedisTemplate<String, String> redisTemplate;
    private final TaskExecutor applicationTaskExecutor;
    private final SpeechRepository speechRepository;
    private final TransactionTemplate transactionTemplate;

    public record JobRequested(Long speechId, String jobToken, Map<String, String> jobData) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(JobRequested event) {
        applicationTaskExecutor.execute(() -> publish(event));
    }

    void publish(JobRequested event) {
        try {
            redisTemplate.opsForStream().add(STREAM_KEY, event.jobData());
            redisTemplate.opsForStream().trim(STREAM_KEY, MAX_STREAM_LENGTH, true); // approximate trim(~)
        } catch (Exception e) {
            log.error("비언어 분석 작업 발행 실패 → FAILED로 전환 (Speech ID: {}): {}", event.speechId(), e.toString());
            transactionTemplate.executeWithoutResult(status -> speechRepository.findById(event.speechId())
                    .filter(s -> event.jobToken().equals(s.getNonVerbalJobToken()))
                    .ifPresent(s -> s.setNonVerbalStatus(AnalysisStatus.FAILED)));
        }
    }
}
