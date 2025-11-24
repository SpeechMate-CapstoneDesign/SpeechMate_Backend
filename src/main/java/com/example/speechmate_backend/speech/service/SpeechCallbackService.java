package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.common.exception.SpeechNotFoundException;
import com.example.speechmate_backend.fcm.FcmService;
import com.example.speechmate_backend.speech.AnalysisStatus;
import com.example.speechmate_backend.speech.controller.dto.NonVerbalAnalysisResponse;
import com.example.speechmate_backend.speech.domain.NonVerbalAnalysisResult;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.NonVerbalAnalysisResultRepository;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpeechCallbackService {

    private final SpeechRepository speechRepository;
    private final NonVerbalAnalysisResultRepository nonVerbalAnalysisResultRepository;
    private final ObjectMapper objectMapper;
    // (WebSocket/SSE 등 실시간 알림 서비스가 있다면 주입)
    private final FcmService fcmService;

    public void saveNonVerbalResult(Long speechId, NonVerbalAnalysisResponse response) {
        // 1. Speech 엔티티 조회
        Speech speech = speechRepository.findById(speechId)
                .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);

        String fcmToken = speech.getUser().getFcmToken();

        // [실패 콜백 처리] Python이 null을 보낸 경우 (분석 로직 자체 실패)
        if (response == null || response.getStatistics() == null) {
            log.error("Python 서버로부터 null 또는 빈 응답을 받았습니다. (Speech ID: {})", speechId);
            // 상태만 FAILED로 변경하고 저장 로직을 종료합니다.
            speech.setNonVerbalStatus(AnalysisStatus.FAILED);
            speechRepository.save(speech);
            return;
        }
        // 2. 이미 결과가 있는지 다시 한번 확인
        if (speech.getNonVerbalAnalysisResult() != null) {
            log.warn("비언어적 분석 콜백 중복 수신 (Speech ID: {}). 저장을 건너뜁니다.", speechId);
            return;
        }

        try {
            // 3. 분석 결과(Entity) 생성 (ObjectMapper 주입)
            NonVerbalAnalysisResult resultEntity = NonVerbalAnalysisResult.builder()
                    .speech(speech)
                    .response(response)
                    .objectMapper(objectMapper) // JSON 직렬화를 위해 ObjectMapper 전달
                    .build();

            // 4. 결과 저장
            nonVerbalAnalysisResultRepository.save(resultEntity);

            // 5. Speech 엔티티에 연관관계 설정 (업데이트)
            speech.setNonVerbalAnalysisResult(resultEntity);

            speech.setNonVerbalStatus(AnalysisStatus.COMPLETED);
            speechRepository.save(speech);

            log.info("비언어적 분석 콜백 저장 완료 (Speech ID: {})", speechId);

            // 6. 클라이언트에게 WebSocket/SSE 등으로 "분석 완료" 알림 전송
            fcmService.sendAnalysisCompletedNotification(fcmToken, speechId, speech.getTitle());
        } catch (Exception e) {
            log.error("Python 비언어적 분석 콜백 저장 실패 (Speech ID: {}): {}", speechId, e.getMessage(), e);
            speech.setNonVerbalStatus(AnalysisStatus.FAILED);
            speechRepository.save(speech);
            // (에러 처리 로직, 예: 재시도 큐로 전송)
        }
    }
}
