package com.example.speechmate_backend.speech.controller.dto;

import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.domain.Speech;
import lombok.Builder;

@Builder
public record SpeechResultDto(
        Long id,
        String sttContent,
        String fileUrl,
        AnalysisResultDto analysisResult
) {

    @Builder
    public static SpeechResultDto from(Speech speech, String fileUrl) {

        return SpeechResultDto.builder()
                .id(speech.getId())
                .sttContent(speech.getContent())
                .fileUrl(fileUrl)
                .analysisResult(AnalysisResultDto.from(speech.getAnalysisResult()))
                .build();
    }
}
