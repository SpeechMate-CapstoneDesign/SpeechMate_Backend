package com.example.core.speech.controller.dto;

import lombok.Builder;

@Builder
public record SpeechContentResponse(
        String content
) {
    public static SpeechContentResponse of(String content) {
        return SpeechContentResponse.builder()
                .content(content)
                .build();
    }
}
