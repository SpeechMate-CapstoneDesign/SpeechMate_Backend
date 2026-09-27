package com.example.speechmate_backend.speech;

import com.example.speechmate_backend.common.exception.UploadLimitExceededException;
import com.example.speechmate_backend.config.redis.RedisUtil;
import com.example.speechmate_backend.fcm.FirebaseConfig;
import com.example.speechmate_backend.s3.config.S3Config;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.controller.SpeechRestClient;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.SpeechCustomRepository;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.speech.returnzero.ReturnZeroClient;
import com.example.speechmate_backend.speech.service.SpeechAnalysisResultService;
import com.example.speechmate_backend.speech.service.SpeechService;
import com.example.speechmate_backend.user.domain.OauthInfo;
import com.example.speechmate_backend.user.domain.SkillType;
import com.example.speechmate_backend.user.domain.User;
import com.example.speechmate_backend.user.domain.UserSkill;
import com.example.speechmate_backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest
public class SpeechTest {
    @Autowired
    private SpeechService speechService;

    @Autowired
    private SpeechRepository speechRepository;

    // 기존 MockBean
    @MockBean
    private SpeechRestClient speechRestClient;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    // 추가 MockBean: SpeechService의 다른 의존성들
    @MockBean
    private UserRepository userRepository;

    @MockBean
    private S3UploadPresignedUrlService s3UploadPresignedUrlService;

    @MockBean
    private S3Config s3Config;


    @MockBean
    private SpeechAnalysisResultService speechAnalysisResultService;

    @MockBean
    private SpeechCustomRepository speechCustomRepository;

    @MockBean
    private CacheManager cacheManager;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;  // RedisTemplate 타입 확인 (Object는 예시)

    @MockBean
    private ReturnZeroClient returnZeroClient;

    @MockBean
    private FirebaseConfig firebaseConfig;


    private Speech speech;

    @BeforeEach
    void setUp() {
        speechRepository.deleteAll();
        OauthInfo oauthInfo = new OauthInfo(); // 실제 OauthInfo 객체
        speech = new Speech();         // 실제 Speech 객체

// 2. User 객체를 먼저 생성합니다. (skills는 아직 비어있음)
        User user = User.builder()
                .id(1L)
                .oauthInfo(oauthInfo)
                .build();

// 3. 생성된 user 객체를 참조하여 UserSkill 객체들을 생성합니다.
        UserSkill skill1 = new UserSkill(user, SkillType.APPROPRIATE_PACE);
        UserSkill skill2 = new UserSkill(user, SkillType.CLEAR_PRONUNCIATION);
        speech = new Speech();
        speech.setFileUrl("test-file-key.mp3");

        user.getSkills().add(skill1);
        user.getSkills().add(skill2);
        user.addSpeech(speech);
        speech.setUser(user);
        speech = speechRepository.save(speech);
        // Mock 설정: 필요 시 추가 (예: userRepository가 호출되면 null 반환 등)
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());  // 예시
    }

    @Test
    @DisplayName("transcribe 메소드에 동시 요청이 발생해도 분산 락을 적용한 외부 API는 단 1번만 호출된다")
    void transcribe_should_call_api_only_once_with_distributed_lock() throws InterruptedException {
        // given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 1. 호출되면 100ms 동안 지연시켜 실제 API처럼 동작하게 만듭니다.
        // 2. "Mock STT Result" 라는 가짜 결과를 반환합니다.
        when(speechRestClient.transcribeWithFileFromS3(anyString())).thenAnswer(invocation -> {
            Thread.sleep(100); // 100ms 지연 시뮬레이션
            return "Mock STT Result";
        });

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    // 여러 스레드가 동시에 같은 speechId에 대해 STT 요청
                    speechService.transcribeversionFromS3(speech.getId());
                } catch (Exception e) {
                    // 락 획득 실패 시 IllegalStateException이 발생할 수 있으나,
                    // 테스트의 목적은 API 호출 횟수 검증이므로 예외는 무시합니다.
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드가 끝날 때까지 대기
        executorService.shutdown();

        // then
        // 10개의 스레드가 경쟁했지만, 비용이 발생하는 speechRestClient의 메소드는
        // 오직 1번만 호출되었는지 검증합니다.
        verify(speechRestClient, times(1)).transcribeWithFileFromS3(anyString());

        // 추가 검증: DB에 결과가 잘 저장되었는지 확인
        Speech resultSpeech = speechRepository.findById(speech.getId()).get();
        assertThat(resultSpeech.getContent()).isEqualTo("Mock STT Result");
    }

    @Test
    @DisplayName("transcribe 메소드에 동시 요청이 발생해도 분산 락을 적용하지 않은 외부 API는 모든 스레드만큼 호출된다")
    void transcribe_should_call_api_only_once_without_distributed_lock() throws InterruptedException {
        // given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 가짜 RestClient 설정:
        // 1. 호출되면 100ms 동안 지연시켜 실제 API처럼 동작하게 만듭니다.
        // 2. "Mock STT Result" 라는 가짜 결과를 반환합니다.
        when(speechRestClient.transcribeWithFileFromS3(anyString())).thenAnswer(invocation -> {
            Thread.sleep(100); // 100ms 지연 시뮬레이션
            return "Mock STT Result";
        });

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    // 여러 스레드가 동시에 같은 speechId에 대해 STT 요청
                    speechService.transcribeversionFromS3WithoutLock(speech.getId());
                } catch (Exception e) {
                    // 락 획득 실패 시 IllegalStateException이 발생할 수 있으나,
                    // 테스트의 목적은 API 호출 횟수 검증이므로 예외는 무시합니다.
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드가 끝날 때까지 대기
        executorService.shutdown();

        // then
        // 10개의 스레드가 경쟁했지만, 비용이 발생하는 speechRestClient의 메소드는
        // 오직 1번만 호출되었는지 검증합니다.
        verify(speechRestClient, times(10)).transcribeWithFileFromS3(anyString());

        // 추가 검증: DB에 결과가 잘 저장되었는지 확인
        Speech resultSpeech = speechRepository.findById(speech.getId()).get();
        assertThat(resultSpeech.getContent()).isEqualTo("Mock STT Result");
    }

    @Test
    @DisplayName("같은 날 6번째 업로드 시도 시 UploadLimitExceededException 발생")
    void uploadLimitExceeded_after_five_requests() {
        String userId = "123";
        // 5번은 통과
        for (int i = 0; i < 5; i++) {
            redisUtil.uploadlimit(userId);
        }

        // 6번째는 예외 발생
        assertThrows(UploadLimitExceededException.class,
                () -> redisUtil.uploadlimit(userId));
    }

    @Test
    @DisplayName("비언어 분석 요청은 트랜잭션 커밋 후에만 Stream에 발행된다 (롤백 시 유령 작업 없음)")
    void nonverbal_job_is_published_only_after_commit() {
        // SpeechService는 @MockBean이 아닌 실제 RedisTemplate<String,String>을 쓰므로 실제 Stream을 검증한다
        String streamKey = "nonverbal-analysis-jobs";
        StringRedisTemplate redis = new StringRedisTemplate(redisConnectionFactory);
        redis.delete(streamKey);
        try {
            // 롤백: 안쪽 @Transactional은 바깥 트랜잭션에 참여하므로 바깥이 롤백되면 afterCommit이 실행되지 않아야 한다
            new TransactionTemplate(txManager).execute(status -> {
                speechService.requestNonVerbalAnalysis(speech.getId());
                status.setRollbackOnly();
                return null;
            });
            assertThat(redis.opsForStream().size(streamKey)).isZero();
            assertThat(speechRepository.findById(speech.getId()).get().getNonVerbalStatus())
                    .isEqualTo(AnalysisStatus.NOT_STARTED);

            // 커밋: 정확히 1건 발행(speechId + s3Key 포함)되고 상태는 IN_PROGRESS
            speechService.requestNonVerbalAnalysis(speech.getId());
            List<MapRecord<String, Object, Object>> records = redis.opsForStream().range(streamKey, Range.unbounded());
            assertThat(records).hasSize(1);
            assertThat((String) records.get(0).getValue().get("job"))
                    .contains("\"speechId\":" + speech.getId())
                    .contains("test-file-key.mp3");
            assertThat(speechRepository.findById(speech.getId()).get().getNonVerbalStatus())
                    .isEqualTo(AnalysisStatus.IN_PROGRESS);
        } finally {
            redis.delete(streamKey);
        }
    }

}
