package com.example.speechmate_backend;

import com.example.speechmate_backend.oauth.KakaoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@EnableConfigurationProperties(value = {KakaoProperties.class})
@EnableFeignClients
@SpringBootApplication
public class SpeechMateBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpeechMateBackendApplication.class, args);
    }

}
