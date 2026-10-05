package com.example.speechmate_backend.speech;

import com.example.speechmate_backend.common.exception.SpeechNotFoundException;
import com.example.speechmate_backend.config.security.JwtUtil;
import com.example.speechmate_backend.fcm.FirebaseConfig;
import com.example.speechmate_backend.s3.config.S3Config;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.returnzero.ReturnZeroClient;
import com.example.speechmate_backend.speech.service.SpeechService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * speechId를 받는 모든 엔드포인트가 "토큰의 userId"를 서비스에 넘기는지 검증한다 (IDOR 차단의 컨트롤러 쪽 절반).
 * 실제 JwtFilter를 통과시키므로 토큰 → CustomUserDetails 경로도 함께 덮인다.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SpeechControllerTest {

    private static final long SPEECH_ID = 5L;
    private static final long USER_ID = 7L;

    @Autowired MockMvc mockMvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean SpeechService speechService;
    @MockBean S3Config s3Config;
    @MockBean S3UploadPresignedUrlService s3UploadPresignedUrlService;
    @MockBean FirebaseConfig firebaseConfig;
    @MockBean ReturnZeroClient returnZeroClient;

    private String bearer() {
        return "Bearer " + jwtUtil.createJwt(USER_ID, "access", 1);
    }

    private void call(HttpMethod method, String path) throws Exception {
        mockMvc.perform(request(method, path).header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("speechId 엔드포인트 9개 모두 토큰의 userId를 서비스에 전달한다")
    void every_speech_endpoint_passes_authenticated_user_id() throws Exception {
        call(HttpMethod.GET, "/api/speech/" + SPEECH_ID);
        verify(speechService).getSpeechById(SPEECH_ID, USER_ID);

        call(HttpMethod.GET, "/api/speech/" + SPEECH_ID + "/speechConfig");
        verify(speechService).getSpeechConfigById(SPEECH_ID, USER_ID);

        call(HttpMethod.GET, "/api/speech/" + SPEECH_ID + "/content");
        verify(speechService).getSpeechContentById(SPEECH_ID, USER_ID);

        call(HttpMethod.GET, "/api/speech/" + SPEECH_ID + "/contentAnalysis");
        verify(speechService).getSpeechContentAnalysisById(SPEECH_ID, USER_ID);

        call(HttpMethod.GET, "/api/speech/" + SPEECH_ID + "/verbalAnalysis");
        verify(speechService).getSpeechVerbalAnalysisById(SPEECH_ID, USER_ID);

        call(HttpMethod.POST, "/api/speech/analyze/" + SPEECH_ID);
        verify(speechService).analyze(SPEECH_ID, USER_ID);

        call(HttpMethod.POST, "/api/speech/rtzrstt/" + SPEECH_ID);
        verify(speechService).rtzrStt(SPEECH_ID, USER_ID, true);

        call(HttpMethod.POST, "/api/speech/nonverbal/" + SPEECH_ID);
        verify(speechService).requestNonVerbalAnalysis(SPEECH_ID, USER_ID);

        call(HttpMethod.DELETE, "/api/speech/delete/" + SPEECH_ID);
        verify(speechService).deleteSpeechById(SPEECH_ID, USER_ID);
    }

    @Test
    @DisplayName("커스텀 예외는 자기 상태 코드와 공통 봉투로 나간다 (404 스피치 없음)")
    void smate_exception_keeps_its_status_and_envelope() throws Exception {
        when(speechService.getSpeechById(SPEECH_ID, USER_ID)).thenThrow(SpeechNotFoundException.EXCEPTION);

        mockMvc.perform(request(HttpMethod.GET, "/api/speech/" + SPEECH_ID).header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.resultCode").value(404))
                .andExpect(jsonPath("$.data").value("존재하지 않는 스피치"));
    }

    @Test
    @DisplayName("예상 못 한 예외는 스택 대신 공통 봉투의 500으로 나간다")
    void unexpected_exception_becomes_plain_500() throws Exception {
        when(speechService.getSpeechById(SPEECH_ID, USER_ID)).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(request(HttpMethod.GET, "/api/speech/" + SPEECH_ID).header("Authorization", bearer()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.resultCode").value(500))
                .andExpect(jsonPath("$.data").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("토큰 없이 호출하면 서비스까지 가지 않는다")
    void rejects_without_token() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, "/api/speech/" + SPEECH_ID))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(speechService);
    }

    @Test
    @DisplayName("예전에 permitAll이던 /api/speech/test/** 는 더 이상 열려 있지 않다 (벤더 토큰 반환 엔드포인트 제거)")
    void test_endpoints_are_gone_and_not_public() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/api/speech/test/returnzero/token"))
                .andExpect(status().is4xxClientError());
    }
}
