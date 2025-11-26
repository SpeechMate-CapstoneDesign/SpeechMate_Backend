package com.example.speechmate_backend.speech.controller.dto;

import com.example.speechmate_backend.speech.domain.NonVerbalAnalysisResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NonVerbalAnalysisResponse {

    private int totalCount;
    private Map<String, List<EventDetailDto>> results;

    public static NonVerbalAnalysisResponse from(NonVerbalAnalysisResult entity, ObjectMapper objectMapper) {
        if (entity == null) {
            return null;
        }

        String rawJson = entity.getRawResultJson();

        if (rawJson == null || rawJson.isEmpty() || rawJson.equals("{}")) {
            log.warn("Non-Verbal analysis result is empty for Entity ID {}", entity.getId());
            return NonVerbalAnalysisResponse.builder().totalCount(0).results(Collections.emptyMap()).build();
        }

        // 1. DB의 raw JSON 문자열을 NonVerbalAnalysisResponse DTO 구조 자체로 역직렬화
        try {
            // ObjectMapper.readValue를 사용하여 JSON String을 DTO 객체로 바로 변환
            return objectMapper.readValue(rawJson, NonVerbalAnalysisResponse.class);

        } catch (JsonProcessingException e) {
            log.error("원시 JSON 데이터 -> Final DTO 역직렬화 실패. DB 저장된 데이터 확인 필요.", e);
            return NonVerbalAnalysisResponse.builder().totalCount(0).results(Collections.emptyMap()).build();
        }
    }
}
