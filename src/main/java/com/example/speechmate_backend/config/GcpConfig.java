/*
package com.example.speechmate_backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class GcpConfig {


    @Bean
    public Storage storage() throws IOException {
        return StorageOptions.newBuilder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build()
                .getService();
    }

    @Bean
    public SpeechClient speechClient() throws IOException {
        return SpeechClient.create();
    }
}
*/
