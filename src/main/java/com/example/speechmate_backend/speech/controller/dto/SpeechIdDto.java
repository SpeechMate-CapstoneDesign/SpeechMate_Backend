package com.example.speechmate_backend.speech.controller.dto;

import lombok.Builder;

@Builder
public record SpeechIdDto(
        Long speechId
) {
    public static SpeechIdDto of(Long speechId) {
        return SpeechIdDto.builder().speechId(speechId).build();
    }
}
