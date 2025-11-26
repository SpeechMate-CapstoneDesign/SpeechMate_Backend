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
import java.util.Base64;

@Slf4j
@Configuration
public class FirebaseConfig {

    //base64
    @Value("${firebase.key}")
    private String fcmPrivateKeyBase64;

    @PostConstruct
    public void initializeFirebase() {

        if (fcmPrivateKeyBase64 == null || fcmPrivateKeyBase64.trim().isEmpty()) {
            log.warn("🚨 Firebase 초기화 스킵: firebase.key-base64 값이 비어있음");
            return;
        }

        try {
            // 1. Base64 → JSON 디코딩
            byte[] decodedBytes = Base64.getDecoder().decode(fcmPrivateKeyBase64);
            InputStream serviceAccount = new ByteArrayInputStream(decodedBytes);

            // 2. Firebase 초기화
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("✅ Firebase 초기화 완료 (Base64 방식)");

        } catch (Exception e) {
            log.error("❌ Firebase 초기화 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Firebase 키 초기화 실패", e);
        }

    }
}
