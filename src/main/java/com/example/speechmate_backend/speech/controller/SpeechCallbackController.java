package com.example.speechmate_backend.speech.controller;

import com.example.speechmate_backend.speech.controller.dto.NonVerbalAnalysisCallbackRequest;
import com.example.speechmate_backend.speech.service.SpeechCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/callback/speech")
public class SpeechCallbackController {

    private final SpeechCallbackService speechCallbackService;

    // 콜백은 permitAll 경로라 워커와 공유하는 시크릿 헤더로만 발신자를 확인한다 (.env CALLBACK_SECRET)
    @Value("${callback.secret}")
    private String callbackSecret;

    @Operation(summary = "콜백(프론트는 신경 X)", description = "파이썬 서버에서 콜백해서 비언어적 분석 결과를 받아옵니다")
    @PostMapping("/non-verbal")
    public ResponseEntity<Void> receiveNonVerbalAnalysisResult(
            @RequestHeader(value = "X-Callback-Secret", required = false) String secret,
            @RequestBody NonVerbalAnalysisCallbackRequest callbackRequest) {

        if (secret == null || !MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8), callbackSecret.getBytes(StandardCharsets.UTF_8))) {
            log.warn("비언어 분석 콜백 시크릿 불일치 (Speech ID: {})", callbackRequest.getSpeechId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        speechCallbackService.saveNonVerbalResult(
                callbackRequest.getSpeechId(),
                callbackRequest.getResponse(),
                callbackRequest.getJobToken()
        );
        return ResponseEntity.ok().build();
    }
}
