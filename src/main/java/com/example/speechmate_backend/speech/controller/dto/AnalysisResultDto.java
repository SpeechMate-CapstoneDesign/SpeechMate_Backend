package com.example.speechmate_backend.speech.controller.dto;

import com.example.speechmate_backend.speech.domain.AnalysisResult;
import lombok.Builder;

@Builder
public record AnalysisResultDto(
        String summary,
        String keywords,
        String improvementPoints,
        int logicalCoherenceScore,
        String scoreExplanation,
        String expectedQuestions,
        String feedback
) {


    public static AnalysisResultDto from(AnalysisResult entity) {
        if (entity == null) {
            return null;
        }
        return AnalysisResultDto.builder()
                .summary(entity.getSummary())
                .keywords(entity.getKeywords())
                .improvementPoints(entity.getImprovementPoints())
                .logicalCoherenceScore(entity.getLogicalCoherenceScore())
                .scoreExplanation(entity.getScoreExplanation())
                .expectedQuestions(entity.getExpectedQuestions())
                .feedback(entity.getFeedback())
                .build();
    }

}
