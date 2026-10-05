package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.common.exception.*;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.AnalysisStatus;
import com.example.speechmate_backend.speech.controller.dto.SentenceDto;
import com.example.speechmate_backend.speech.controller.dto.SttGateResponse;
import com.example.speechmate_backend.speech.domain.VerbalAnalysisResult;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("STT 접수: 파일키가 없으면 404이고 아무것도 접수되지 않는다")
    void stt_gate_rejects_missing_file_key() {
        speech.setFileUrl(null);
        assertThatThrownBy(() -> speechService.rtzrStt(1L, 1L)).isSameAs(SpeechFileKeyNotFoundException.EXCEPTION);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("STT 접수: 처음이면 IN_PROGRESS로 바꾸고 커밋 뒤 실행될 작업 이벤트를 1건 발행한다")
    void stt_gate_accepts_and_publishes() {
        SttGateResponse res = speechService.rtzrStt(1L, 1L);

        assertThat(res.sttStatus()).isEqualTo(AnalysisStatus.IN_PROGRESS);
        assertThat(speech.getSttStatus()).isEqualTo(AnalysisStatus.IN_PROGRESS);
        assertThat(speech.getSttJobToken()).isNotBlank();
        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue()).isEqualTo(new SttJobRunner.JobRequested(1L, speech.getSttJobToken()));
    }

    @Test
    @DisplayName("STT 접수: 진행 중이면 상태만 돌려주고 다시 접수하지 않는다. FAILED면 다시 접수한다")
    void stt_gate_in_progress_and_failed() {
        speech.setSttStatus(AnalysisStatus.IN_PROGRESS);
        assertThat(speechService.rtzrStt(1L, 1L).sttStatus()).isEqualTo(AnalysisStatus.IN_PROGRESS);
        verify(eventPublisher, never()).publishEvent(any(Object.class));

        speech.setSttStatus(AnalysisStatus.FAILED);
        assertThat(speechService.rtzrStt(1L, 1L).sttStatus()).isEqualTo(AnalysisStatus.IN_PROGRESS);
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("STT 접수: 이미 문장이 저장돼 있으면 COMPLETED와 문장을 바로 돌려준다 (상태 컬럼 도입 전 데이터 포함)")
    void stt_gate_returns_cached_sentences() throws Exception {
        VerbalAnalysisResult verbal = new VerbalAnalysisResult();
        verbal.setSentencesJson("[{\"startTime\":0,\"sentence\":\"안녕\"}]");
        speech.setVerbalAnalysisResult(verbal);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(List.of(new SentenceDto(0, "안녕")));

        SttGateResponse res = speechService.rtzrStt(1L, 1L);

        assertThat(res.sttStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(res.sentences()).containsExactly(new SentenceDto(0, "안녕"));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("비언어 분석 요청: 작업 직렬화 실패는 NonVerbalAnalysisException, 상태는 바뀌지 않음")
    void nonverbal_wraps_serialization_failure() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("x") {});

        assertThatThrownBy(() -> speechService.requestNonVerbalAnalysis(1L, 1L)).isInstanceOf(NonVerbalAnalysisException.class);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(speechRepository, never()).save(any());
    }
}
