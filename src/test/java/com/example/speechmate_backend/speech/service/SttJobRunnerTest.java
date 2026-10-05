package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.common.exception.FileTooLargeException;
import com.example.speechmate_backend.speech.AnalysisStatus;
import com.example.speechmate_backend.speech.controller.dto.TranscriptionResponse;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.domain.VerbalAnalysisResult;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.speech.returnzero.ReturnZeroClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 백그라운드 STT 실행기. 트랜잭션 템플릿은 콜백을 그대로 실행하는 가짜로 대체한다. */
@ExtendWith(MockitoExtension.class)
class SttJobRunnerTest {

    @Mock SpeechRepository speechRepository;
    @Mock ReturnZeroClient returnZeroClient;
    @Mock SpeechAnalysisResultService speechAnalysisResultService;

    private SttJobRunner runner;
    private Speech speech;
    private static final String TOKEN = "tok";
    private static final TranscriptionResponse TR =
            new TranscriptionResponse("rid", "completed", new TranscriptionResponse.Results(List.of(
                    new TranscriptionResponse.Utterance(10, 100, 0, "s", List.of(), "안녕")), true));

    @BeforeEach
    void setUp() {
        TransactionTemplate passThrough = new TransactionTemplate() {
            @Override public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
        runner = new SttJobRunner(new SyncTaskExecutor(), speechRepository, returnZeroClient,
                speechAnalysisResultService, new ObjectMapper(), passThrough);
        speech = new Speech();
        speech.setFileUrl("a.mp3");
        speech.setSttStatus(AnalysisStatus.IN_PROGRESS);
        speech.setSttJobToken(TOKEN);
        when(speechRepository.findById(1L)).thenReturn(Optional.of(speech));
    }

    private void attachVerbalResultOnAnalyze() {
        doAnswer(inv -> { ((Speech) inv.getArgument(0)).setVerbalAnalysisResult(new VerbalAnalysisResult()); return ""; })
                .when(speechAnalysisResultService).verbalanalyze(any(), any());
    }

    @Test
    @DisplayName("성공: 원본 JSON·문장 JSON을 저장하고 COMPLETED")
    void success_stores_result_and_completes() {
        when(returnZeroClient.rtzrSttFromS3("a.mp3")).thenReturn("rid");
        when(returnZeroClient.rtzrTranscription("rid")).thenReturn(TR);
        attachVerbalResultOnAnalyze();

        runner.onCommitted(new SttJobRunner.JobRequested(1L, TOKEN));

        assertThat(speech.getSttStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(speech.getRawTranscription()).contains("\"id\":\"rid\"");
        assertThat(speech.getVerbalAnalysisResult().getSentencesJson()).contains("\"sentence\":\"안녕\"").contains("\"startTime\":10");
        verify(speechRepository).save(speech);
    }

    @Test
    @DisplayName("원본 JSON 캐시가 있으면 외부 STT를 부르지 않고 언어 분석만 다시 한다")
    void cached_transcription_skips_external_call() {
        speech.setRawTranscription("{\"id\":\"old\",\"status\":\"completed\",\"results\":{\"utterances\":[],\"verified\":true}}");
        attachVerbalResultOnAnalyze();

        runner.run(new SttJobRunner.JobRequested(1L, TOKEN));

        verifyNoInteractions(returnZeroClient);
        assertThat(speech.getSttStatus()).isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    @DisplayName("외부 STT 실패(용량 초과·네트워크)는 FAILED로 남기고 예외는 삼킨다. 사용자는 재요청하면 된다")
    void failure_marks_failed() {
        when(returnZeroClient.rtzrSttFromS3("a.mp3")).thenThrow(FileTooLargeException.EXCEPTION);

        runner.run(new SttJobRunner.JobRequested(1L, TOKEN));

        assertThat(speech.getSttStatus()).isEqualTo(AnalysisStatus.FAILED);
        verify(speechAnalysisResultService, never()).verbalanalyze(any(), any());
    }

    @Test
    @DisplayName("토큰이 다르면(그 사이 새 시도 시작) 아무것도 하지 않는다")
    void stale_token_is_ignored() {
        runner.run(new SttJobRunner.JobRequested(1L, "stale"));

        verifyNoInteractions(returnZeroClient);
        assertThat(speech.getSttStatus()).isEqualTo(AnalysisStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("분석 뒤 VerbalAnalysisResult가 없으면(저장 실패) FAILED")
    void missing_analysis_result_marks_failed() {
        when(returnZeroClient.rtzrSttFromS3("a.mp3")).thenReturn("rid");
        when(returnZeroClient.rtzrTranscription("rid")).thenReturn(TR);
        // verbalanalyze(mock)가 결과를 붙이지 않음 → NPE → FAILED

        runner.run(new SttJobRunner.JobRequested(1L, TOKEN));

        assertThat(speech.getSttStatus()).isEqualTo(AnalysisStatus.FAILED);
    }
}
