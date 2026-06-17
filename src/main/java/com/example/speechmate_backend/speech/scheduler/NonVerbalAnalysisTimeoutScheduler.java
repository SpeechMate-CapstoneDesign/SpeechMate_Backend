package com.example.speechmate_backend.speech.scheduler;

import com.example.speechmate_backend.speech.AnalysisStatus;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NonVerbalAnalysisTimeoutScheduler {

    private static final int TIMEOUT_MINUTES = 30;

    private final SpeechRepository speechRepository;

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void detectTimedOutJobs() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<Speech> timedOut = speechRepository
                .findByNonVerbalStatusAndModifiedAtBefore(AnalysisStatus.IN_PROGRESS, threshold);

        if (timedOut.isEmpty()) return;

        timedOut.forEach(s -> {
            log.warn("비언어 분석 타임아웃 (speechId={}, modifiedAt={})", s.getId(), s.getModifiedAt());
            s.setNonVerbalStatus(AnalysisStatus.FAILED);
        });

        log.info("IN_PROGRESS 타임아웃 처리 {}건 → FAILED", timedOut.size());
    }
}
