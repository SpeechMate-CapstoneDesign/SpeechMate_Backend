package com.example.speechmate_backend.speech.controller.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record SpeechPagingResponseDto(
        List<SpeechAnalysisResponseDto> speeches,
        boolean hasNext,
        CursorDto cursordto
) {
}

