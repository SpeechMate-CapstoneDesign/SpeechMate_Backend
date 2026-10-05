package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.common.exception.*;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.controller.dto.TranscriptionResponse;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.speech.returnzero.ReturnZeroClient;
import com.example.speechmate_backend.user.domain.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 외부 호출이 실패했을 때 어떤 예외로 나가는지. 도메인 예외는 그대로, 그 외는 단계별 도메인 예외로 감싼다. */
@ExtendWith(MockitoExtension.class)
class SpeechServiceExceptionTest {

    @Mock SpeechRepository speechRepository;
    @Mock S3UploadPresignedUrlService s3UploadPresignedUrlService;
    @Mock SpeechAnalysisResultService speechAnalysisResultService;
    @Mock ReturnZeroClient returnZeroClient;
    @Mock ObjectMapper objectMapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks SpeechService speechService;

    private Speech speech;

    @BeforeEach
    void setUp() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", 1L);
        speech = new Speech();
        speech.setUser(user);
        speech.setFileUrl("a.mp3");
        speech.setContent("대본");
        when(speechRepository.findById(1L)).thenReturn(Optional.of(speech));
    }

    @Test
    @DisplayName("analyze: GPT 호출 중 예상 못 한 예외는 AiAnalysisException으로")
    void analyze_wraps_unexpected() {
        when(speechAnalysisResultService.analyzeText(any(), anyString())).thenThrow(new IllegalStateException("gpt down"));
        assertThatThrownBy(() -> speechService.analyze(1L, 1L)).isInstanceOf(AiAnalysisException.class);
    }

    @Test
    @DisplayName("analyze: 도메인 예외는 감싸지 않고 그대로")
    void analyze_passes_domain_exception() {
        when(speechAnalysisResultService.analyzeText(any(), anyString())).thenThrow(ReturnZeroException.EXCEPTION);
        assertThatThrownBy(() -> speechService.analyze(1L, 1L)).isSameAs(ReturnZeroException.EXCEPTION);
    }

    @Test
    @DisplayName("rtzrStt: 파일 용량 초과(400)는 500으로 바뀌지 않고 그대로 나간다")
    void stt_passes_file_too_large() {
        when(returnZeroClient.rtzrSttFromS3("a.mp3")).thenThrow(FileTooLargeException.EXCEPTION);
        assertThatThrownBy(() -> speechService.rtzrStt(1L, 1L)).isSameAs(FileTooLargeException.EXCEPTION);
    }

    @Test
    @DisplayName("rtzrStt: 네트워크 등 예상 못 한 예외는 ReturnZeroException으로")
    void stt_wraps_unexpected() {
        when(returnZeroClient.rtzrSttFromS3("a.mp3")).thenThrow(new RuntimeException("timeout"));
        assertThatThrownBy(() -> speechService.rtzrStt(1L, 1L)).isInstanceOf(ReturnZeroException.class);
    }

    @Test
    @DisplayName("rtzrStt: 분석 뒤에도 VerbalAnalysisResult가 없으면 ReturnZeroException")
    void stt_fails_when_analysis_result_missing() throws Exception {
        when(returnZeroClient.rtzrSttFromS3("a.mp3")).thenReturn("rid");
        when(returnZeroClient.rtzrTranscription("rid")).thenReturn(
                new TranscriptionResponse("rid", "completed", new TranscriptionResponse.Results(List.of(), true)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        // verbalanalyze(mock)가 결과 엔티티를 붙이지 않음

        assertThatThrownBy(() -> speechService.rtzrStt(1L, 1L)).isInstanceOf(ReturnZeroException.class);
    }

    @Test
    @DisplayName("비언어 분석 요청: 작업 직렬화 실패는 NonVerbalAnalysisException, 상태는 바뀌지 않음")
    void nonverbal_wraps_serialization_failure() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("x") {});

        assertThatThrownBy(() -> speechService.requestNonVerbalAnalysis(1L, 1L)).isInstanceOf(NonVerbalAnalysisException.class);
        verify(eventPublisher, never()).publishEvent(any());
        verify(speechRepository, never()).save(any());
    }
}
