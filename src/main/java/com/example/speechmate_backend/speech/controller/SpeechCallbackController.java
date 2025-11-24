package com.example.speechmate_backend.speech.controller;

import com.example.speechmate_backend.speech.controller.dto.NonVerbalAnalysisCallbackRequest;
import com.example.speechmate_backend.speech.service.SpeechCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/callback/speech")
public class SpeechCallbackController {

    private final SpeechCallbackService speechCallbackService;

    @Operation(summary = "콜백(프론트는 신경 X)", description = "파이썬 서버에서 콜백해서 비언어적 분석 결과를 받아옵니다")
    @PostMapping("/non-verbal")
    public ResponseEntity<Void> receiveNonVerbalAnalysisResult(
            @RequestBody NonVerbalAnalysisCallbackRequest callbackRequest) {

        speechCallbackService.saveNonVerbalResult(
                callbackRequest.getSpeechId(),
                callbackRequest.getResponse()
        );
        return ResponseEntity.ok().build();
    }
}
