package com.example.speechmate_backend.speech.returnzero;

import com.amazonaws.services.s3.model.S3Object;
import com.example.speechmate_backend.common.exception.FFmpegException;
import com.example.speechmate_backend.common.exception.FileTooLargeException;
import com.example.speechmate_backend.common.exception.ReturnZeroException;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.controller.dto.TranscriptionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
public class ReturnZeroClient {

    private final WebClient webClient;
    private final ReturnZeroTokenManager returnZeroTokenManager;
    private final ObjectMapper objectMapper;
    private final S3UploadPresignedUrlService s3UploadPresignedUrlService;


    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    // Use WebClient.Builder to create an instance configured for ReturnZero
    public ReturnZeroClient(WebClient.Builder webClientBuilder, ReturnZeroTokenManager returnZeroTokenManager, ObjectMapper objectMapper, S3UploadPresignedUrlService s3UploadPresignedUrlService) {
        this.webClient = webClientBuilder
                .baseUrl("https://openapi.vito.ai")
                .build();
        this.returnZeroTokenManager = returnZeroTokenManager;
        this.objectMapper = objectMapper;
        this.s3UploadPresignedUrlService = s3UploadPresignedUrlService;
    }

    public String rtzrSttFromS3(String fileKey) {
        File tempVideoFile = null;
        File tempAudioFile = null;
        S3Object s3Object = null;

        try {
            log.info("S3에서 파일 다운로드 시작: {}", fileKey);
            s3Object = s3UploadPresignedUrlService.getObject(fileKey);

            // 1. 임시 파일로 저장
            String fileExtension = getFileExtension(fileKey);
            tempVideoFile = File.createTempFile("speech-temp", "." + fileExtension);

            try (InputStream is = s3Object.getObjectContent()) {
                Files.copy(is, tempVideoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("임시 파일 생성 완료. 크기: {} bytes", tempVideoFile.length());

            // 2. mp4/mov/m4a는 mp3로 변환
            if ("mp4".equalsIgnoreCase(fileExtension)
                    || "mov".equalsIgnoreCase(fileExtension)
                    || "m4a".equalsIgnoreCase(fileExtension)) {

                tempAudioFile = File.createTempFile("audio-extracted", ".mp3");
                log.info("ffmpeg 변환 시작");
                runFfmpegConversion(tempVideoFile, tempAudioFile);
                log.info("변환 완료. MP3 크기: {} bytes", tempAudioFile.length());

            } else {
                tempAudioFile = File.createTempFile("audio-original", "." + fileExtension);
                Files.copy(tempVideoFile.toPath(), tempAudioFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            long fileSizeMB = tempAudioFile.length() / (1024 * 1024);
            if (fileSizeMB > 25) {
                log.error("파일 크기 초과: {}MB (최대 25MB)", fileSizeMB);
                throw FileTooLargeException.EXCEPTION;
            }

            // ⭐ 3. ReturnZero API 호출
            log.info("ReturnZero API 호출 시작");
            FileSystemResource fileResource = new FileSystemResource(tempAudioFile);

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", fileResource)
                    .filename("audio." + getFileExtension(tempAudioFile.getName()))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM);

            //기본 모델(sommer), 간투어 필터 false, 단어별 타임스탬프 true
            Map<String, Object> config = Map.of(
                    "use_disfluency_filter", false,
                    "use_word_timestamp", true
            );

            String configJson = objectMapper.writeValueAsString(config);
            builder.part("config", configJson, MediaType.APPLICATION_JSON);


            String accessToken = returnZeroTokenManager.getAccessToken();

            String response = webClient.post()
                    .uri("/v1/transcribe") // ReturnZero STT 엔드포인트
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("Authorization", "Bearer " + accessToken) // ReturnZero 인증 헤더
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class) // ReturnZero는 ID를 반환하므로, String으로 받으면 됩니다.
                    .block();

            JsonNode jsonNode = objectMapper.readTree(response);
            String rtzrId = jsonNode.get("id").asText();

            log.info("ReturnZero에서 받은 응답(Id): {}", rtzrId);
            return rtzrId;
        } catch (Exception e) {
            log.error("ReturnZero Id를 받아오는 중에 오류 발생", e);
            throw ReturnZeroException.EXCEPTION;
        } finally {
            if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
            if (tempAudioFile != null && tempAudioFile.exists()) tempAudioFile.delete();
            if (s3Object != null) try { s3Object.close(); } catch (Exception ignored) {}
        }
    }

    public TranscriptionResponse rtzrTranscription(String rtzrid) {
        String accessToken = returnZeroTokenManager.getAccessToken();

        return webClient.get()
                .uri("/v1/transcribe/{rtzrid}", rtzrid)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(TranscriptionResponse.class) // DTO로 변환
                .filter(response -> {
                    log.info("Transcribe ID {} 상태: {}", rtzrid, response.status());
                    return "completed".equals(response.status());
                })
                .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofSeconds(5))) // 5초 대기 후 반복
                .block(Duration.ofMinutes(5)); // 5분 타임아웃

    }

    //test용
    public String testrtzrTranscription(String rtzrid) {
        String accessToken = returnZeroTokenManager.getAccessToken();

        String response = webClient.get()
                .uri("/v1/transcribe/{rtzrid}", rtzrid)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class) // ✅ DTO로 변환
                .block();

        return response;
    }


    // ffmpeg 로컬 파일 변환
    private void runFfmpegConversion(File inputFile, File outputFile) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-i", inputFile.getAbsolutePath(),
                "-vn",
                "-acodec", "libmp3lame",
                "-b:a", "128k",
                "-ar", "44100",
                "-ac", "2",
                "-y",
                outputFile.getAbsolutePath()
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 로그 출력
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.lines().forEach(line -> log.info("[FFMPEG] {}", line));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw FFmpegException.EXECPTION;
        }
    }


    private String getFileExtension(String fileKey) {
        return fileKey.substring(fileKey.lastIndexOf(".")+1).toLowerCase();
    }

}
