/*
package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.s3.MediaFileExtension;
import com.example.speechmate_backend.s3.controller.dto.VoiceRecordDto;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.google.cloud.speech.v1.*;

import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
public class GoogleSttService {

    private final Storage storage;
    private final SpeechClient speechClient;

    @Value("${cloud.gcp.gcs.bucket-name}") // application.yml 등에서 버킷 이름 주입
    private String gcsBucketName;

    public VoiceRecordDto generateGcsSignedUrlForSpeech(Long userId, Long speechId, MediaFileExtension extension) {
        String fileExtension = extension.getUploadExtension();
        String objectName = getSpeechFileName(userId, speechId, fileExtension);
        URL signedUrl = storage.signUrl(
                BlobInfo.newBuilder(gcsBucketName, objectName).build(),
                15, TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withV4Signature()
        );
        return VoiceRecordDto.of(signedUrl.toString(), objectName);
    }

    */
/*1분이상의 음성(WAV)파일에만 적용됨*//*

    public String transcribe(String gcsUri) {
        try {
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16) // 실제 파일 형식으로 변경
                    .setLanguageCode("ko-KR")
                    .setUseEnhanced(true)   //
                    .build();

            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setUri("gs://" + gcsBucketName + "/" + gcsUri)
                    .build();

            LongRunningRecognizeResponse response = speechClient.longRunningRecognizeAsync(config, audio).get();
            return response.getResultsList().stream()
                    .flatMap(result -> result.getAlternativesList().stream())
                    .map(SpeechRecognitionAlternative::getTranscript)
                    .reduce((a, b) -> a + " " + b)
                    .orElseThrow(() -> new IllegalStateException("STT 결과가 비어 있습니다"));
        } catch (Exception e) {
            throw new IllegalStateException("STT 작업 실패: " + e.getMessage(), e);
        }

    }

    private String getSpeechFileName(Long userId, Long speechId, String fileExtension) {
        return "user/"
                + userId.toString()
                + "/speech/"
                + speechId
                + "/"
                + UUID.randomUUID()
                +"."
                +fileExtension;
    }

}
*/
