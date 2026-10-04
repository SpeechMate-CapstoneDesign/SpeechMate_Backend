package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.common.exception.SpeechNotFoundException;
import com.example.speechmate_backend.fcm.FcmService;
import com.example.speechmate_backend.speech.AnalysisStatus;
import com.example.speechmate_backend.speech.controller.dto.NonVerbalAnalysisResponse;
import com.example.speechmate_backend.speech.domain.NonVerbalAnalysisResult;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpeechCallbackService {

    private final SpeechRepository speechRepository;
    private final ObjectMapper objectMapper;
    private final FcmService fcmService;

    /**
     * 워커 콜백. 큐가 at-least-once라 같은 speechId의 콜백이 늦게 오거나 두 번 올 수 있다.
     * - 토큰이 현재 시도와 다르면(이전 시도의 늦은 콜백) 무시 → 새 결과를 옛 FAILED가 덮어쓰지 않는다.
     * - 같은 시도의 재전달(콜백 후 ACK 전 워커 사망)은 COMPLETED 상태로 걸러진다.
     * 결과 저장과 상태 변경은 한 트랜잭션이라 "결과는 있는데 상태는 FAILED" 조합이 생기지 않는다.
     */
    @Transactional
    public void saveNonVerbalResult(Long speechId, NonVerbalAnalysisResponse response, String jobToken) {
        Speech speech = speechRepository.findById(speechId)
                .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);

        if (!Objects.equals(speech.getNonVerbalJobToken(), jobToken)) {
            log.warn("비언어 분석 콜백 토큰 불일치, 이전 시도의 콜백으로 보고 무시 (Speech ID: {}, token={})", speechId, jobToken);
            return;
        }

        if (speech.getNonVerbalStatus() == AnalysisStatus.COMPLETED) {
            log.warn("비언어적 분석 콜백 중복 수신 (Speech ID: {}). 저장을 건너뜁니다.", speechId);
            return;
        }

        if (response == null) {
            log.error("Python 서버로부터 null 또는 빈 응답을 받았습니다. (Speech ID: {})", speechId);
            speech.setNonVerbalStatus(AnalysisStatus.FAILED);
            return;
        }

        NonVerbalAnalysisResult resultEntity = NonVerbalAnalysisResult.builder()
                .speech(speech)
                .response(response)
                .objectMapper(objectMapper)
                .build();

        // 이전 시도의 결과가 남아 있으면 먼저 지운다 (speech_id unique라 insert가 delete보다 먼저 나가면 충돌).
        if (speech.getNonVerbalAnalysisResult() != null) {
            speech.setNonVerbalAnalysisResult(null); // orphanRemoval → delete
            speechRepository.flush();
        }
        speech.setNonVerbalAnalysisResult(resultEntity); // cascade ALL → insert
        speech.setNonVerbalStatus(AnalysisStatus.COMPLETED);
        log.info("비언어적 분석 콜백 저장 완료 (Speech ID: {})", speechId);

        try {
            fcmService.sendAnalysisCompletedNotification(speech.getUser().getFcmToken(), speechId, speech.getTitle());
        } catch (Exception e) {
            log.error("분석 완료 푸시 전송 실패, 결과 저장에는 영향 없음 (Speech ID: {}): {}", speechId, e.getMessage());
        }
    }
}
