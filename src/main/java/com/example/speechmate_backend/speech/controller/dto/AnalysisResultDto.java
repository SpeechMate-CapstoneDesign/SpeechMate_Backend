package com.example.speechmate_backend.speech.controller.dto;

public record AnalysisResultDto(
        String summary,
        String keywords,
        String improvementPoints,
        int logicalCoherenceScore,
        String scoreExplanation,
        String expectedQuestions,
        String feedback
) {
}
