package com.example.speechmate_backend.speech;

import com.example.speechmate_backend.fcm.FcmService;
import com.example.speechmate_backend.fcm.FirebaseConfig;
import com.example.speechmate_backend.s3.config.S3Config;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.controller.dto.NonVerbalAnalysisResponse;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.NonVerbalAnalysisResultRepository;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.speech.returnzero.ReturnZeroClient;
import com.example.speechmate_backend.speech.service.SpeechCallbackService;
import com.example.speechmate_backend.user.domain.OauthInfo;
import com.example.speechmate_backend.user.domain.User;
import com.example.speechmate_backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스트림이 at-least-once라 같은 speechId의 콜백이 늦게/두 번 온다. 그때 상태가 꼬이지 않는지 본다.
 */
@ActiveProfiles("test")
@SpringBootTest
class SpeechCallbackServiceTest {

    @Autowired SpeechCallbackService callbackService;
    @Autowired SpeechRepository speechRepository;
    @Autowired UserRepository userRepository;
    @Autowired NonVerbalAnalysisResultRepository resultRepository;

    @MockBean FcmService fcmService;
    @MockBean S3Config s3Config;
    @MockBean S3UploadPresignedUrlService s3UploadPresignedUrlService;
    @MockBean FirebaseConfig firebaseConfig;
    @MockBean ReturnZeroClient returnZeroClient;

    private Long speechId;
    private final NonVerbalAnalysisResponse ok = new NonVerbalAnalysisResponse(1, Collections.emptyMap());

    @BeforeEach
    void setUp() {
        speechRepository.deleteAll();
        userRepository.deleteAll();
        User user = userRepository.save(User.builder().oauthInfo(new OauthInfo()).build());
        Speech speech = new Speech();
        speech.setFileUrl("v.mp4");
        speech.setUser(user);
        speech.setNonVerbalStatus(AnalysisStatus.IN_PROGRESS);
        speech.setNonVerbalJobToken("attempt-2");
        speechId = speechRepository.save(speech).getId();
    }

    private Speech reload() {
        return speechRepository.findById(speechId).orElseThrow();
    }

    @Test
    @DisplayName("이전 시도의 늦은 FAILED 콜백은 무시된다 — 새 시도의 COMPLETED를 덮어쓰지 않는다")
    void stale_failed_callback_is_ignored() {
        callbackService.saveNonVerbalResult(speechId, ok, "attempt-2");
        assertThat(reload().getNonVerbalStatus()).isEqualTo(AnalysisStatus.COMPLETED);

        callbackService.saveNonVerbalResult(speechId, null, "attempt-1"); // DLQ로 간 옛 메시지의 FAILED 콜백

        assertThat(reload().getNonVerbalStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(resultRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 시도의 재전달(콜백 후 ACK 전 워커 사망)은 결과를 한 번만 저장한다")
    void duplicate_completed_callback_is_idempotent() {
        callbackService.saveNonVerbalResult(speechId, ok, "attempt-2");
        callbackService.saveNonVerbalResult(speechId, ok, "attempt-2");

        assertThat(reload().getNonVerbalStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(resultRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("FAILED 뒤 재요청(새 토큰)의 COMPLETED는 이전 결과가 남아 있어도 저장된다 — 영구 루프 방지")
    void retry_after_failed_replaces_old_result() {
        callbackService.saveNonVerbalResult(speechId, ok, "attempt-2");
        Speech s = reload();
        s.setNonVerbalStatus(AnalysisStatus.FAILED);       // 예전 코드가 남긴 "결과는 있는데 FAILED" 상태를 흉내
        s.setNonVerbalJobToken("attempt-3");               // 사용자 재요청으로 새 시도 발행
        speechRepository.save(s);

        callbackService.saveNonVerbalResult(speechId, ok, "attempt-3");

        assertThat(reload().getNonVerbalStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(resultRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 시도의 FAILED 콜백은 그대로 FAILED로 반영된다")
    void current_failed_callback_applies() {
        callbackService.saveNonVerbalResult(speechId, null, "attempt-2");
        assertThat(reload().getNonVerbalStatus()).isEqualTo(AnalysisStatus.FAILED);
    }
}
