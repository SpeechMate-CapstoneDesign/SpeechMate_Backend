package com.example.speechmate_backend.speech.scheduler;

import com.example.speechmate_backend.speech.AnalysisStatus;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** JVM 재시작 등으로 실행 스레드가 사라져 IN_PROGRESS에 멈춘 작업을 FAILED로 돌려 재요청 가능하게 한다. */
@ExtendWith(MockitoExtension.class)
class AnalysisTimeoutSchedulerTest {

    @Mock SpeechRepository speechRepository;
    @InjectMocks NonVerbalAnalysisTimeoutScheduler scheduler;

    @Test
    @DisplayName("STT: 오래된 IN_PROGRESS는 FAILED로")
    void stt_timeout_marks_failed() {
        Speech stuck = new Speech();
        stuck.setSttStatus(AnalysisStatus.IN_PROGRESS);
        when(speechRepository.findBySttStatusAndModifiedAtBefore(eq(AnalysisStatus.IN_PROGRESS), any(LocalDateTime.class)))
                .thenReturn(List.of(stuck));

        scheduler.detectTimedOutSttJobs();

        assertThat(stuck.getSttStatus()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    @DisplayName("비언어 분석: 오래된 IN_PROGRESS는 FAILED로, 없으면 아무 일도 없음")
    void nonverbal_timeout_marks_failed() {
        Speech stuck = new Speech();
        stuck.setNonVerbalStatus(AnalysisStatus.IN_PROGRESS);
        when(speechRepository.findByNonVerbalStatusAndModifiedAtBefore(eq(AnalysisStatus.IN_PROGRESS), any(LocalDateTime.class)))
                .thenReturn(List.of(stuck)).thenReturn(List.of());

        scheduler.detectTimedOutJobs();
        scheduler.detectTimedOutJobs();

        assertThat(stuck.getNonVerbalStatus()).isEqualTo(AnalysisStatus.FAILED);
    }
}
