package com.example.speechmate_backend.speech.controller.dto;

import com.example.speechmate_backend.speech.domain.AnalysisResult;
import lombok.Builder;

import java.util.List;

@Builder
public record AnalysisResultDto(
        String summary,
        String keywords,
        List<String> improvementPoints,
        List<String> expectedQuestions,
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
                .expectedQuestions(entity.getExpectedQuestions())
                .feedback(entity.getFeedback())
                .build();
    }

}
