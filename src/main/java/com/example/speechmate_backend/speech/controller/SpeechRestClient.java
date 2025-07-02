package com.example.speechmate_backend.speech.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class SpeechRestClient {

    private final RestClient restClient;


    public SpeechRestClient(@Value("${spring.ai.openai.api-key}") String openAiApiKey) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1/audio")
                .defaultHeader("Authorization", "Bearer " + openAiApiKey)
                .build();
    }


    public String transcribe(Resource audioFile) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", audioFile);
        body.add("model", "whisper-1");
        body.add("language", "ko");
        body.add("response_format", "text");

        return restClient.post()
                .uri("/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);
    }

}
