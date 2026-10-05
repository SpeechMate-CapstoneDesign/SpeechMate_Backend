package com.example.speechmate_backend.speech.controller.dto;

import com.example.speechmate_backend.speech.AnalysisStatus;

import java.util.List;

/** STT 접수/조회 응답. COMPLETED일 때만 sentences가 채워진다. */
public record SttGateResponse(
        AnalysisStatus sttStatus,
        List<SentenceDto> sentences
) {
    public static SttGateResponse statusOnly(AnalysisStatus status) {
        return new SttGateResponse(status, null);
    }

    public static SttGateResponse completed(List<SentenceDto> sentences) {
        return new SttGateResponse(AnalysisStatus.COMPLETED, sentences);
    }
}
