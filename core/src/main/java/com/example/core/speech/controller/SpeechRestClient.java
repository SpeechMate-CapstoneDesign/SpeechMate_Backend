package com.example.core.speech.controller;

import com.amazonaws.services.s3.model.S3Object;
import com.example.core.common.exception.FFmpegException;
import com.example.core.common.exception.FileTooLargeException;
import com.example.core.common.exception.WhisperException;
import com.example.core.s3.MediaFileExtension;
import com.example.core.s3.service.S3UploadPresignedUrlService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class SpeechRestClient {

    private final RestClient restClient;
    private final WebClient webClient;
    private final S3UploadPresignedUrlService s3UploadPresignedUrlService;

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

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

            String fileExtension = getFileExtension(fileKey);
            MediaFileExtension mediaType = MediaFileExtension.valueOf(fileExtension.toUpperCase());

            // 2. InputStream → 임시 파일로 저장
            File tempFile = File.createTempFile("speech", mediaType.getUploadExtension());
            Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            FileSystemResource fileResource = new FileSystemResource(tempFile);

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", fileResource)
                    .filename("speech." + mediaType.getUploadExtension())
                            .contentType(MediaType.valueOf(mediaType.getMimeType()));
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
            throw WhisperException.EXCEPTION;
        }
    }

    public String transcribeWithFileFromS3(String fileKey) {
        File tempVideoFile = null;
        File tempAudioFile = null;
        S3Object s3Object = null;

        try {
            log.info("S3에서 파일 다운로드 시작: {}", fileKey);
            s3Object = s3UploadPresignedUrlService.getObject(fileKey);

            // 1️⃣ 임시 파일로 저장
            String fileExtension = getFileExtension(fileKey);
            tempVideoFile = File.createTempFile("speech-temp", "." + fileExtension);

            try (InputStream is = s3Object.getObjectContent()) {
                Files.copy(is, tempVideoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("임시 파일 생성 완료. 크기: {} bytes", tempVideoFile.length());

            // 2️⃣ mp4/mov/m4a는 mp3로 변환
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

            // 3️⃣ Whisper API 호출
            log.info("whisper 호출");
            FileSystemResource fileResource = new FileSystemResource(tempAudioFile);
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", fileResource)
                    .filename("speech." + getFileExtension(tempAudioFile.getName()))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM);
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
            log.info("whisper에서 받은 내용: {}",response);
            return response;

        } catch (Exception e) {
            log.error("Whisper 변환 중 오류 발생", e);
            throw WhisperException.EXCEPTION;
        } finally {
            if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
            if (tempAudioFile != null && tempAudioFile.exists()) tempAudioFile.delete();
            if (s3Object != null) try { s3Object.close(); } catch (Exception ignored) {}
        }
    }


    //mp4, mov, m4a ->mp3 변환 메소드
    public String transcribeLargeFile(String fileKey) {
        File tempVideoFile = null;
        File tempAudioFile = null;
        S3Object s3Object = null;

        try {
            log.info("S3에서 파일 다운로드 시작: {}", fileKey);
            s3Object = s3UploadPresignedUrlService.getObject(fileKey);

            // 1️⃣ S3 → 임시 비디오/오디오 파일로 저장
            String fileExtension = getFileExtension(fileKey);
            tempVideoFile = File.createTempFile("video-temp", "." + fileExtension);

            try (InputStream is = s3Object.getObjectContent()) {
                Files.copy(is, tempVideoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("임시 파일 생성 완료. 크기: {} bytes", tempVideoFile.length());

            // 2️⃣ mp4/mov/m4a는 mp3로 변환, 나머지는 그대로 사용
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

            if (tempAudioFile.length() > 25 * 1024 * 1024) {
                throw FileTooLargeException.EXCEPTION;
            }

            return "변환 성공";

        } catch (Exception e) {
            log.error("Whisper 변환 중 오류 발생", e);
            throw WhisperException.EXCEPTION;
        } finally {
            if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
            if (tempAudioFile != null && tempAudioFile.exists()) tempAudioFile.delete();
            if (s3Object != null) try { s3Object.close(); } catch (Exception ignored) {}
        }
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
