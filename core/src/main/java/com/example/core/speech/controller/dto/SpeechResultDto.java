package com.example.core.speech.controller.dto;

import com.example.core.speech.domain.Speech;
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

    public static SpeechResultDto fromE(Speech speech, String s3Url) {
        AnalysisResultDto analysisResultDto = null;
        if (speech.getAnalysisResult() != null) {
            analysisResultDto = AnalysisResultDto.from(speech.getAnalysisResult());
        }

        return SpeechResultDto.builder()
                .id(speech.getId())
                .sttContent(speech.getContent())
                .fileUrl(s3Url)
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
