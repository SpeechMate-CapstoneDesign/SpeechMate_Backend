package com.example.core.speech.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SpeechAnalysisResponseDto(
        Long speechId,
        LocalDateTime createdAt,
        String fileUrl,
        String content,
        String summary,
        String keywords,
        List<String> improvementPoints,
        String feedback,
        List<String> expectedQuestions,
        boolean isAnalyzed
) {
}
