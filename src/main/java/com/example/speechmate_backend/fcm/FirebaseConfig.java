package com.example.speechmate_backend.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
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
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase 이미 초기화되어 있음, 초기화 스킵");
            return;
        }

        try {
            InputStream serviceAccount = new ByteArrayInputStream(
                    fcmPrivateKeyJson.getBytes(StandardCharsets.UTF_8));

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("Firebase 초기화 완료");
        } catch (IOException e) {
            throw new RuntimeException("Firebase Key 로드 실패", e);
        }
    }
}
