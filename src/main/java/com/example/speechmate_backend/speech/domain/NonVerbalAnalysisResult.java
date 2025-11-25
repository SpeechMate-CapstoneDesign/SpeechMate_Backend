package com.example.speechmate_backend.speech.domain;

import com.example.speechmate_backend.speech.controller.dto.NonVerbalAnalysisResponse;
import com.example.speechmate_backend.speech.controller.dto.StatisticsDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NonVerbalAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "speech_id", nullable = false)
    private Speech speech;

    // --- 1. 통계 (Statistics) ---
    // (개별 컬럼으로 저장되어 DB에서 바로 조회 가능)
    private int totalFloorEvents;
    private int totalCeilingEvents;
    private int totalLipBiteEvents;
    private int totalHandNearFaceEvents;
    private int totalSlantEvents;
    private int totalBlinkEvents;

    private int totalArmsCrossedEvents;
    private int totalHandsBehindBackEvents;
    private int totalHandsRubbingEvents;
    private int totalFigLeafPoseEvents;

    // --- 2. 타임스탬프 로그 (EventLog) ---
    // (리스트를 JSON 문자열로 변환하여 TEXT 컬럼에 저장)
    @Lob
    @Column(name = "analysis_log_json", columnDefinition = "TEXT")
    private String analysisLogJson;

    public void setSpeech(Speech speech) {
        this.speech = speech;
    }


    @Builder
    public NonVerbalAnalysisResult(Speech speech, NonVerbalAnalysisResponse response, ObjectMapper objectMapper) {
        this.speech = speech;

        // 1. 통계(Statistics) 저장
        if (response.getStatistics() != null) {
            StatisticsDto stats = response.getStatistics();
            this.totalFloorEvents = stats.getTotalFloorEvents();
            this.totalCeilingEvents = stats.getTotalCeilingEvents();
            this.totalLipBiteEvents = stats.getTotalLipBiteEvents();
            this.totalHandNearFaceEvents = stats.getTotalHandNearFaceEvents();
            this.totalSlantEvents = stats.getTotalSlantEvents();
            this.totalBlinkEvents = stats.getTotalBlinkEvents();

            this.totalArmsCrossedEvents = stats.getTotalArmsCrossedEvents();
            this.totalHandsBehindBackEvents = stats.getTotalHandsBehindBackEvents();
            this.totalHandsRubbingEvents = stats.getTotalHandsRubbingEvents();
            this.totalFigLeafPoseEvents = stats.getTotalFigLeafPoseEvents();
        }

        if (response.getAnalysisLog() != null) {
            try {
                this.analysisLogJson = objectMapper.writeValueAsString(response.getAnalysisLog());
            } catch (JsonProcessingException e) {
                log.error("비언어적 분석 로그(analysisLog) JSON 직렬화 실패. Speech ID: {}", speech.getId(), e);
                this.analysisLogJson = "[]"; // 실패 시 빈 배열 저장
            }
        }
    }
}
