package com.example.speechmate_backend.speech;

import com.example.speechmate_backend.common.exception.UploadLimitExceededException;
import com.example.speechmate_backend.config.redis.RedisUtil;
import com.example.speechmate_backend.fcm.FirebaseConfig;
import com.example.speechmate_backend.s3.config.S3Config;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.controller.dto.TranscriptionResponse;
import com.example.speechmate_backend.speech.domain.VerbalAnalysisResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest
public class SpeechTest {
    @Autowired
    private SpeechService speechService;

    @Autowired
    private SpeechRepository speechRepository;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    // user 테이블은 실제로 만든다 (speech.user_id FK가 걸려 있어 유저 행이 있어야 speech를 저장할 수 있음)
    @Autowired
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
        userRepository.deleteAll();
        OauthInfo oauthInfo = new OauthInfo(); // 실제 OauthInfo 객체
        speech = new Speech();         // 실제 Speech 객체

// 2. User 객체를 먼저 생성합니다. (skills는 아직 비어있음)
        User user = userRepository.save(User.builder()
                .oauthInfo(oauthInfo)
                .build());

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
    }

    @Test
    @DisplayName("rtzrStt 동시 요청 10건에도 분산 락 덕분에 외부 STT API는 1번만 호출된다")
    void rtzrStt_should_call_api_only_once_with_distributed_lock() throws InterruptedException {
        // given: 외부 STT 호출은 100ms 걸리는 가짜. 분석 서비스(mock)는 결과 엔티티만 붙여 준다
        when(returnZeroClient.rtzrSttFromS3(anyString())).thenAnswer(inv -> {
            Thread.sleep(100);
            return "rtzr-id";
        });
        when(returnZeroClient.rtzrTranscription("rtzr-id"))
                .thenReturn(new TranscriptionResponse("rtzr-id", "completed",
                        new TranscriptionResponse.Results(List.of(), true)));
        doAnswer(inv -> {
            Speech s = inv.getArgument(0);
            s.setVerbalAnalysisResult(new VerbalAnalysisResult());
            return "";
        }).when(speechAnalysisResultService).verbalanalyze(any(), any());

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Long userId = speech.getUser().getId();

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    speechService.rtzrStt(speech.getId(), userId);
                } catch (Exception e) {
                    // 락 대기 초과 등은 무시. 검증 대상은 외부 API 호출 횟수
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        // then: 첫 스레드가 커밋한 rawTranscription을 뒤 스레드들이 캐시로 읽어 API를 다시 부르지 않는다
        verify(returnZeroClient, times(1)).rtzrSttFromS3(anyString());
        assertThat(speechRepository.findById(speech.getId()).get().getRawTranscription()).contains("rtzr-id");
    }

    @Test
    @DisplayName("같은 날 6번째 업로드 시도 시 UploadLimitExceededException 발생")
    void uploadLimitExceeded_after_five_requests() {
        String userId = "123";
        // 카운터 키는 자정까지 살아 있어서, 같은 날 두 번째 실행부터는 이전 실행분이 남는다
        new StringRedisTemplate(redisConnectionFactory).delete("upload:" + userId + ":" + java.time.LocalDate.now());
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
    void nonverbal_job_is_published_only_after_commit() throws Exception {
        // SpeechService는 @MockBean이 아닌 실제 RedisTemplate<String,String>을 쓰므로 실제 Stream을 검증한다
        String streamKey = "nonverbal-analysis-jobs";
        StringRedisTemplate redis = new StringRedisTemplate(redisConnectionFactory);
        redis.delete(streamKey);
        try {
            // 롤백: 안쪽 @Transactional은 바깥 트랜잭션에 참여하므로 바깥이 롤백되면 afterCommit이 실행되지 않아야 한다
            new TransactionTemplate(txManager).execute(status -> {
                speechService.requestNonVerbalAnalysis(speech.getId(), speech.getUser().getId());
                status.setRollbackOnly();
                return null;
            });
            assertThat(redis.opsForStream().size(streamKey)).isZero();
            assertThat(speechRepository.findById(speech.getId()).get().getNonVerbalStatus())
                    .isEqualTo(AnalysisStatus.NOT_STARTED);

            // 커밋: 정확히 1건 발행(speechId + s3Key 포함)되고 상태는 IN_PROGRESS.
            // 발행은 커밋 뒤 executor 스레드에서 하므로(NonVerbalJobPublisher) 잠깐 기다린다
            speechService.requestNonVerbalAnalysis(speech.getId(), speech.getUser().getId());
            List<MapRecord<String, Object, Object>> records = List.of();
            for (int i = 0; i < 50 && records.isEmpty(); i++) {
                Thread.sleep(100);
                records = redis.opsForStream().range(streamKey, Range.unbounded());
            }
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
