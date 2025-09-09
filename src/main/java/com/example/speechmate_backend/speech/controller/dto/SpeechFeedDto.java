package com.example.speechmate_backend.speech.controller.dto;

import com.example.speechmate_backend.speech.domain.Speech;

import java.time.LocalDateTime;

public record SpeechFeedDto(
        Long id,
        String title,            // 발표 파일 이름
        LocalDateTime createdAt,        // 등록 날짜 (문자열 변환)
        Long duration,
        String fileType,         // AUDIO / VIDEO
        String fileUrl,
        String presentationContext,
        String audience,
        String location
) {



}