package com.example.speechmate_backend.speech.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NonVerbalAnalysisCallbackRequest {
    private Long speechId; // 어떤 스피치에 대한 결과인지
    private NonVerbalAnalysisResponse response; // 기존에 만든 분석 결과 DTO
}
