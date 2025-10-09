package com.example.core.speech.controller.dto;

import java.util.List;
import java.util.Map;

public record GptResponse(
        String summary,
        String keywords,
        List<String> improvementPoints,
        List<String> expectedQuestions,
        String feedback,
        Map<String, Integer> repeatedWords
) {
}
