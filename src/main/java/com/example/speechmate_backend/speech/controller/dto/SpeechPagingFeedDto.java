package com.example.speechmate_backend.speech.controller.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record SpeechPagingFeedDto(
        List<SpeechFeedDto> speeches,
        boolean hasNext,
        CursorDto cursordto
) {
}
