package com.example.speechmate_backend.speech.controller.dto;

import com.example.speechmate_backend.speech.domain.NonVerbalAnalysisResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

@Slf4j
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NonVerbalAnalysisResponse {
    private StatisticsDto statistics;      // 1. 통계 객체
    private List<EventLogDto> analysisLog; // 2. 타임스탬프 로그 리스트

    public static NonVerbalAnalysisResponse from(NonVerbalAnalysisResult entity, ObjectMapper objectMapper) {
        if (entity == null) {
            return null;
        }

        // 1. Statistics DTO 생성 (엔티티의 필드를 StatisticsDto의 생성자로 전달)
        StatisticsDto statsDto = new StatisticsDto(
                entity.getTotalFloorEvents(),
                entity.getTotalCeilingEvents(),
                entity.getTotalLeftEvents(),
                entity.getTotalRightEvents(),
                entity.getTotalLipBiteEvents(),
                entity.getTotalHandNearFaceEvents(),
                entity.getTotalSlantEvents(),
                entity.getTotalBlinkEvents(),
                entity.getTotalArmsCrossedEvents(),
                entity.getTotalHandsBehindBackEvents(),
                entity.getTotalHandsRubbingEvents(),
                entity.getTotalFigLeafPoseEvents()
        );

        // 2. JSON 문자열을 List<EventLogDto>로 역직렬화
        List<EventLogDto> logList;
        String jsonLog = entity.getAnalysisLogJson();

        if (jsonLog != null && !jsonLog.isEmpty()) {
            try {
                // List<EventLogDto> 타입으로 역직렬화
                logList = objectMapper.readValue(jsonLog, new TypeReference<List<EventLogDto>>() {});
            } catch (JsonProcessingException e) {
                log.error("비언어적 분석 로그(analysisLogJson) 역직렬화 실패. Entity ID: {}", entity.getId(), e);
                logList = Collections.emptyList();
            }
        } else {
            logList = Collections.emptyList();
        }

        // 3. 최종 DTO 반환
        return NonVerbalAnalysisResponse.builder()
                .statistics(statsDto)
                .analysisLog(logList)
                .build();
    }
}
