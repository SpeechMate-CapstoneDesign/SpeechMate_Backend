package com.example.speechmate_backend.speech.controller.dto;

import com.example.speechmate_backend.speech.domain.Speech;
import lombok.Builder;

@Builder
public record SpeechConfigDto(
        Long duration,
        String fileType,
        String presentationContext,
        String audience,
        String location,
        String createdAt,
        String fileUrl
) {
    public static SpeechConfigDto from(Speech speech, String s3Url) {
        return SpeechConfigDto.builder()
                .duration(speech.getDuration())
                .fileType(speech.getFileType())
                .presentationContext(speech.getPresentationContext())
                .audience(speech.getAudience())
                .location(speech.getLocation())
                .createdAt(speech.getCreatedAt().toString())
                .fileUrl(s3Url)
                .build();
    }
}
