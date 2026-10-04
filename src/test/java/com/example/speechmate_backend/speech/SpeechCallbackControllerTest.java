package com.example.speechmate_backend.speech;

import com.example.speechmate_backend.fcm.FirebaseConfig;
import com.example.speechmate_backend.s3.config.S3Config;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.returnzero.ReturnZeroClient;
import com.example.speechmate_backend.speech.service.SpeechCallbackService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SpeechCallbackControllerTest {

    private static final String URL = "/api/callback/speech/non-verbal";
    private static final String BODY = "{\"speechId\": 7, \"response\": null}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpeechCallbackService speechCallbackService;

    // 외부 자격증명이 필요한 빈은 SpeechTest와 같은 방식으로 mock (S3 클라이언트, Firebase, STT)
    @MockBean
    private S3Config s3Config;

    @MockBean
    private S3UploadPresignedUrlService s3UploadPresignedUrlService;

    @MockBean
    private FirebaseConfig firebaseConfig;

    @MockBean
    private ReturnZeroClient returnZeroClient;

    @Test
    @DisplayName("시크릿 헤더가 없거나 틀리면 401이고 서비스는 호출되지 않는다 (permitAll 경로라 이 헤더가 유일한 발신자 검증)")
    void rejects_without_or_with_wrong_secret() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("X-Callback-Secret", "wrong"))
                .andExpect(status().isUnauthorized());
        verify(speechCallbackService, never()).saveNonVerbalResult(any(), any(), any());
    }

    @Test
    @DisplayName("시크릿이 맞으면 200이고 결과 저장 서비스가 호출된다")
    void accepts_with_correct_secret() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("X-Callback-Secret", "test-callback-secret"))
                .andExpect(status().isOk());
        verify(speechCallbackService).saveNonVerbalResult(eq(7L), any(), any());
    }
}
