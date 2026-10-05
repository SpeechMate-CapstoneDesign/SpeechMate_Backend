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
    private static final int STT_TIMEOUT_MINUTES = 10;

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

    /** STT는 외부 폴링 최대 5분 + 변환 시간. JVM 재시작으로 실행 스레드가 사라진 작업을 여기서 거둔다. */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void detectTimedOutSttJobs() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STT_TIMEOUT_MINUTES);
        List<Speech> timedOut = speechRepository.findBySttStatusAndModifiedAtBefore(AnalysisStatus.IN_PROGRESS, threshold);
        timedOut.forEach(s -> {
            log.warn("STT 타임아웃 (speechId={}, modifiedAt={})", s.getId(), s.getModifiedAt());
            s.setSttStatus(AnalysisStatus.FAILED);
        });
    }
}
