package com.example.core.speech.controller.dto;

import com.example.core.speech.domain.VerbalAnalysisResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;

import java.util.List;
import java.util.Map;


@Builder
public record VerbalAnalysisDto(
        int wordCnt,
        int syllableCnt,
        List<FillerSection> fillers,
        List<RepeatedWord> repeatedWords,
        List<SilenceSection> silences
) {

    public record FillerSection(String word, List<Integer> timestamps) {}
    public record RepeatedWord(String word, int count) {}
    public record SilenceSection(
            int duration,
            int startTime,
            int endTime,
            String wordBefore,
            String wordAfter
    ) {}

    public static VerbalAnalysisDto from(VerbalAnalysisResult entity) {
        if (entity == null) {
            return null;
        }
        return VerbalAnalysisDto.builder()
                .wordCnt((int) entity.getWordCnt())
                .syllableCnt((int)entity.getSyllableCnt())
                .fillers(parseFillers(entity.getFillerJson()))
                .repeatedWords(parseRepeatedWords(entity.getRepeatedWordsJson()))
                .silences(parseSilences(entity.getSilenceJson()))
                .build();
    }


    private static List<FillerSection> parseFillers(String json) {
        if (json == null) return List.of();
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, List<Integer>> map = mapper.readValue(json, new TypeReference<>() {});
            return map.entrySet().stream()
                    .map(e -> new FillerSection(e.getKey(), e.getValue()))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<RepeatedWord> parseRepeatedWords(String json) {
        if (json == null) return List.of();
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Integer> map = mapper.readValue(json, new TypeReference<>() {});
            return map.entrySet().stream()
                    .map(e -> new RepeatedWord(e.getKey(), e.getValue()))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<SilenceSection> parseSilences(String json) {
        if (json == null) return List.of();
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}