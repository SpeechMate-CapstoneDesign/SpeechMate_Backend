package com.example.speechmate_backend.speech;

public enum AnalysisStatus {
    NOT_STARTED, // 분석 시작 전 (기본값)
    IN_PROGRESS, // 분석 중 (Spring이 Redis에 발행 완료)
    COMPLETED,   // 분석 완료 (Python이 콜백 완료)
    FAILED       // 분석 실패 (Python이 실패 콜백)
}
