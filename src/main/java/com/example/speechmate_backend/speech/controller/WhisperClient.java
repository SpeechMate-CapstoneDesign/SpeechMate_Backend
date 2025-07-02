package com.example.speechmate_backend.speech.controller;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@FeignClient(name = "whisperClient", url = "${spring.ai.openai.base-url}")
public interface WhisperClient {

    @PostMapping(value = "/v1/audio/transcriptions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    String transcribe(
            @RequestPart("file") MultipartFile file,
            @RequestPart("model") String model,
            @RequestPart("language") String language,
            @RequestPart("response_format") String responseFormat,
            @RequestHeader("Authorization") String authorizationHeader
    );
}
