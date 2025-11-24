package com.example.speechmate_backend.speech.controller.dto;

import com.example.speechmate_backend.speech.AnalysisStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NonVerbalAnalysisGateResponse {

    // 현재 분석 상태 (NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED)
    private final AnalysisStatus status;

    // COMPLETED일 경우에만 값이 채워짐
    private final NonVerbalAnalysisResponse result;

    // Helper method: status만 반환할 때 사용
    public static NonVerbalAnalysisGateResponse statusOnly(AnalysisStatus status) {
        return NonVerbalAnalysisGateResponse.builder()
                .status(status)
                .build();
    }
}
