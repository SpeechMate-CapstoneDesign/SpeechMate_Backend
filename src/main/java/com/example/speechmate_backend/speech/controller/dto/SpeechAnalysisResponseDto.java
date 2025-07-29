package com.example.speechmate_backend.speech.controller.dto;

import java.time.LocalDateTime;

public record SpeechAnalysisResponseDto(
        Long speechId,
        LocalDateTime createdAt,
        String fileUrl,
        String content,
        String summary,
        String keywords,
        String improvementPoints,
        int logicalCoherenceScore,
        String feedback,
        String scoreExplanation,
        String expectedQuestions

) {
}
