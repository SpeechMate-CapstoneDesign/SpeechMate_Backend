package com.example.speechmate_backend.speech;

import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.speech.service.NonVerbalJobPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 발행이 끝내 실패하면 상태를 FAILED로 돌리되, 그 사이 새 요청(다른 jobToken)이 들어왔으면 건드리지 않는다. */
class NonVerbalJobPublisherTest {

    @SuppressWarnings("unchecked")
    private NonVerbalJobPublisher publisherWithRedisDown(SpeechRepository repo) {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        StreamOperations<String, Object, Object> stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        when(stream.add(anyString(), any(Map.class))).thenThrow(new RedisConnectionFailureException("redis down"));
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new NonVerbalJobPublisher(redis, new SyncTaskExecutor(), repo, new TransactionTemplate(tm));
    }

    @Test
    @DisplayName("XADD 실패 → 같은 jobToken이면 FAILED로 전환해 바로 재요청할 수 있게 한다")
    void publishFailure_marksFailed() {
        Speech speech = new Speech();
        speech.setNonVerbalStatus(AnalysisStatus.IN_PROGRESS);
        speech.setNonVerbalJobToken("t1");
        SpeechRepository repo = mock(SpeechRepository.class);
        when(repo.findById(7L)).thenReturn(Optional.of(speech));

        publisherWithRedisDown(repo).onCommitted(new NonVerbalJobPublisher.JobRequested(7L, "t1", Map.of("job", "{}")));

        assertThat(speech.getNonVerbalStatus()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    @DisplayName("XADD 실패 → 이미 다른 시도(jobToken 다름)가 진행 중이면 그 상태를 건드리지 않는다")
    void publishFailure_leavesNewerAttemptAlone() {
        Speech speech = new Speech();
        speech.setNonVerbalStatus(AnalysisStatus.IN_PROGRESS);
        speech.setNonVerbalJobToken("t2");
        SpeechRepository repo = mock(SpeechRepository.class);
        when(repo.findById(7L)).thenReturn(Optional.of(speech));

        publisherWithRedisDown(repo).onCommitted(new NonVerbalJobPublisher.JobRequested(7L, "t1", Map.of("job", "{}")));

        assertThat(speech.getNonVerbalStatus()).isEqualTo(AnalysisStatus.IN_PROGRESS);
    }
}
