package com.example.speechmate_backend.speech.returnzero;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Slf4j
@Service
public class ReturnZeroTokenManager {

    private final WebClient webClient;
    private String accessToken;
    private LocalDateTime tokenExpiresAt;

    @Value("${returnzero.client-id}")
    private String clientId;

    @Value("${returnzero.client-secret}")
    private String clientSecret;

    public ReturnZeroTokenManager(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://openapi.vito.ai").build();
    }

    public String getAccessToken() {
        if (accessToken == null || isTokenExpired()) {
            issueNewToken();
        }
        return accessToken;
    }

    private void issueNewToken() {
        log.info("ReturnZero 액세스 토큰 재발급을 시도합니다.");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        Map response = webClient.post()
                .uri("/v1/authenticate")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response != null && response.containsKey("access_token")) {
            this.accessToken = (String) response.get("access_token");

            Object expireAtObj = response.get("expire_at");
            long expireAtTimestamp;

            if (expireAtObj instanceof Number) {
                expireAtTimestamp = ((Number) expireAtObj).longValue();
            } else {
                // 예외 처리 (String 등으로 올 경우)
                try {
                    expireAtTimestamp = Long.parseLong(expireAtObj.toString());
                } catch (NumberFormatException e) {
                    log.error("ReturnZero 'expire_at' 필드 파싱 실패: {}", expireAtObj);
                    throw new RuntimeException("ReturnZero 토큰 응답 파싱 실패");
                }
            }

            // Unix 타임스탬프(초)를 LocalDateTime으로 변환합니다.
            this.tokenExpiresAt = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(expireAtTimestamp), // Unix 타임스탬프(초) -> Instant
                    ZoneId.systemDefault()                    // 시스템 기본 시간대
            ).minusMinutes(1);

            log.info("새로운 RTZR 액세스 토큰이 발급되었습니다. 만료 시간: {}", tokenExpiresAt);
        } else {
            log.error("토큰 발급 실패: {}", response);
            throw new RuntimeException("ReturnZero 토큰 발급 실패");
        }
    }

    private boolean isTokenExpired() {
        return tokenExpiresAt != null && LocalDateTime.now().isAfter(tokenExpiresAt);
    }

}
