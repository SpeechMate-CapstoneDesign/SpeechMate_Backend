package com.example.speechmate_backend.speech.controller;

import com.amazonaws.services.s3.model.S3Object;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Service
public class SpeechRestClient {

    private final RestClient restClient;
    private final WebClient webClient;
    private final S3UploadPresignedUrlService s3UploadPresignedUrlService;

    public SpeechRestClient(@Value("${spring.ai.openai.api-key}") String openAiApiKey, S3UploadPresignedUrlService s3UploadPresignedUrlService) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1/audio")
                .defaultHeader("Authorization", "Bearer " + openAiApiKey)
                .build();

        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1/audio")
                .defaultHeader("Authorization", "Bearer " + openAiApiKey)
                .build();
        this.s3UploadPresignedUrlService = s3UploadPresignedUrlService;
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

    /* wav, mp3, m4a 등 다양한 파일 받기 위한 버전 */
    public String transcribeversion2(MultipartFile multipartFile) {
        try {


            // 임시 파일로 저장
            File tempFile = File.createTempFile("speech", ".mp3");
            multipartFile.transferTo(tempFile);

            FileSystemResource fileResource = new FileSystemResource(tempFile);

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", fileResource)
                    .header("Content-Disposition", "form-data; name=\"file\"; filename=\"" + multipartFile.getOriginalFilename() + "\"");
            builder.part("model", "whisper-1");
            builder.part("language", "ko");
            builder.part("response_format", "text");

            String response = webClient.post()
                    .uri("/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 임시 파일 삭제
            tempFile.delete();

            return response;
        } catch (Exception e) {
            throw new RuntimeException("STT 변환 실패: " + e.getMessage(), e);
        }
    }

    /*s3에서 받아와서 */
    public String transcribeversionFromS3(String fileKey) {
        try {

            S3Object s3Object = s3UploadPresignedUrlService.getObject(fileKey);
            InputStream inputStream = s3Object.getObjectContent();

            // 2. InputStream → 임시 파일로 저장
            File tempFile = File.createTempFile("speech", ".mp3");
            Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            FileSystemResource fileResource = new FileSystemResource(tempFile);

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", fileResource)
                    .header("Content-Disposition", "form-data; name=\"file\"; filename=\"speech.mp3\"");
            builder.part("model", "whisper-1");
            builder.part("language", "ko");
            builder.part("response_format", "text");

            String response = webClient.post()
                    .uri("/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 임시 파일 삭제
            tempFile.delete();

            return response;
        } catch (Exception e) {
            throw new RuntimeException("STT 변환 실패: " + e.getMessage(), e);
        }
    }

}
