package com.example.core.speech.controller.dto;

import lombok.Builder;

@Builder
public record SpeechS3CallbackDto(
        Long speechId,
        String s3Url
) {
    public static SpeechS3CallbackDto of(Long speechId, String s3Url) {
        return SpeechS3CallbackDto.builder()
                .speechId(speechId)
                .s3Url(s3Url).build();
    }
}
