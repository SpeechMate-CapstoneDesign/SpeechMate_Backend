package com.example.speechmate_backend.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class FirebaseConfig {


    @Value("${firebase.key}")
    private String fcmPrivateKeyJson;

    @PostConstruct
    public void initializeFirebase() {
        // 1. [FIX] JSON 문자열 정리 및 유효성 검사
        String trimmedJson = fcmPrivateKeyJson != null ? fcmPrivateKeyJson.trim() : "";

        // 2. 이미 초기화되었거나 키가 없으면 건너뛰기
        if (FirebaseApp.getApps().isEmpty() && !trimmedJson.isEmpty() && !trimmedJson.equals("{}")) {

            try {
                Gson gson = new Gson();
                Object jsonObject = gson.fromJson(trimmedJson, Object.class);
                String cleanJson = gson.toJson(jsonObject);

                InputStream serviceAccount = new ByteArrayInputStream(
                        cleanJson.getBytes(StandardCharsets.UTF_8)); // [FIX] 클린된 JSON 사용

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("✅ Firebase 초기화 완료");

            } catch (Exception e) {
                // IOException, MalformedJsonException 등 모든 파싱 오류 포착
                log.error("❌ Firebase 초기화 중 JSON 파싱/IO 오류 발생. 키를 확인하세요. 오류: {}", e.getMessage(), e);
                // 빈 생성 실패를 유도하여 컨텍스트 로드를 중단시킵니다. (필수)
                throw new RuntimeException("Firebase 키 로드 실패 및 초기화 중단", e);
            }
        } else if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase 이미 초기화되어 있음, 초기화 스킵");
        } else {
            log.warn("🚨 Firebase 초기화 건너뛰기: 키 값이 비어있거나 테스트 값으로 설정됨. (키: {})", trimmedJson);
        }
    }
}
