package com.example.core.speech.returnzero;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
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
            int expireAt = (int) response.get("expire_at");
            this.tokenExpiresAt = LocalDateTime.now().plusSeconds(expireAt).minusMinutes(1);
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
