package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.speech.AnalysisStatus;
import com.example.speechmate_backend.speech.controller.dto.SentenceDto;
import com.example.speechmate_backend.speech.controller.dto.TranscriptionResponse;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.speech.returnzero.ReturnZeroClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

/**
 * STT 작업을 커밋 뒤, 요청 스레드 밖에서 돌린다. S3 다운로드 → ffmpeg → ReturnZero 제출 → 완료까지 5초 간격 폴링(최대 5분)이
 * 전부 여기서 일어난다. 예전엔 이 전체가 요청 스레드와 분산 락 안에 있었다.
 *
 * 작업의 진실은 DB의 sttStatus다. JVM이 죽어 이 스레드가 사라지면 IN_PROGRESS가 남고, 타임아웃 스케줄러가 FAILED로 돌려
 * 사용자가 재요청한다. 비언어 분석처럼 Redis Streams를 쓰지 않은 이유: 작업이 외부 서비스 1건 호출이고 다른 런타임으로
 * 넘어가지 않아, 큐가 주는 "워커 분배·재전달"이 필요 없다. DB 상태 + 타임아웃이면 충분하다.
 * ponytail: 재시작 시 rtzrJobId로 폴링을 이어가지 않고 FAILED로 돌린다. 재요청 빈도가 문제 되면 그때 이어받기 추가.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SttJobRunner {

    private final TaskExecutor applicationTaskExecutor;
    private final SpeechRepository speechRepository;
    private final ReturnZeroClient returnZeroClient;
    private final SpeechAnalysisResultService speechAnalysisResultService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public record JobRequested(Long speechId, String jobToken) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(JobRequested event) {
        applicationTaskExecutor.execute(() -> run(event));
    }

    void run(JobRequested event) {
        try {
            Speech snapshot = transactionTemplate.execute(status -> speechRepository.findById(event.speechId()).orElse(null));
            if (snapshot == null || !Objects.equals(snapshot.getSttJobToken(), event.jobToken())) {
                log.warn("STT 작업 건너뜀: 스피치가 없거나 토큰이 바뀜 (Speech ID: {})", event.speechId());
                return;
            }

            TranscriptionResponse transcription;
            String rawJson = snapshot.getRawTranscription();
            if (rawJson != null && !rawJson.isEmpty()) {
                log.info("STT 캐시 사용, 언어 분석만 다시 실행 (Speech ID: {})", event.speechId());
                transcription = objectMapper.readValue(rawJson, TranscriptionResponse.class);
            } else {
                String rtzrId = returnZeroClient.rtzrSttFromS3(snapshot.getFileUrl());
                transcription = returnZeroClient.rtzrTranscription(rtzrId); // 완료까지 폴링. 이제 요청 스레드가 아니다
            }

            String transcriptionJson = objectMapper.writeValueAsString(transcription);
            String sentencesJson = objectMapper.writeValueAsString(toSentences(transcription));

            transactionTemplate.executeWithoutResult(status -> {
                Speech speech = speechRepository.findById(event.speechId()).orElse(null);
                if (speech == null || !Objects.equals(speech.getSttJobToken(), event.jobToken())) {
                    return; // 그 사이 새 시도가 시작됨. 이 결과는 버린다
                }
                speech.setRawTranscription(transcriptionJson);
                speechAnalysisResultService.verbalanalyze(speech, transcription);
                speech.getVerbalAnalysisResult().setSentencesJson(sentencesJson);
                speech.setSttStatus(AnalysisStatus.COMPLETED);
                speechRepository.save(speech);
            });
            log.info("STT 완료 (Speech ID: {})", event.speechId());
        } catch (Exception e) {
            log.error("STT 작업 실패 → FAILED (Speech ID: {})", event.speechId(), e);
            transactionTemplate.executeWithoutResult(status -> speechRepository.findById(event.speechId())
                    .filter(s -> Objects.equals(s.getSttJobToken(), event.jobToken()))
                    .ifPresent(s -> s.setSttStatus(AnalysisStatus.FAILED)));
        }
    }

    static List<SentenceDto> toSentences(TranscriptionResponse transcription) {
        if (transcription.results() == null || transcription.results().utterances() == null) {
            return List.of();
        }
        return transcription.results().utterances().stream().map(SentenceDto::from).toList();
    }
}
