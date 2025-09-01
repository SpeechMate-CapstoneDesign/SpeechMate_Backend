package com.example.speechmate_backend.speech.controller.dto;

import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.domain.Speech;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record SpeechResultDto(
        Long id,
        String sttContent,
        String fileUrl,
        String title,
        String presentationContext,
        String audience,
        String location,
        Long duration,
        String fileType,
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

    public static SpeechResultDto fromE(Speech speech) {
        AnalysisResultDto analysisResultDto = null;
        if (speech.getAnalysisResult() != null) {
            analysisResultDto = AnalysisResultDto.from(speech.getAnalysisResult());
        }

        return SpeechResultDto.builder()
                .id(speech.getId())
                .sttContent(speech.getContent())
                .fileUrl(speech.getFileUrl())
                .title(speech.getTitle())
                .presentationContext(speech.getPresentationContext())
                .audience(speech.getAudience())
                .location(speech.getLocation())
                .duration(speech.getDuration())
                .fileType(speech.getFileType())
                .analysisResult(analysisResultDto)
                .build();
    }
}
