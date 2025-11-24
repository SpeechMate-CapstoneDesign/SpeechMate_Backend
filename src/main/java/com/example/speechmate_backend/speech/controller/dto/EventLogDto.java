package com.example.speechmate_backend.speech.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EventLogDto {
    private String timestamp; // "HH:MM:SS" 형식
    private String event;     // "눈 깜빡임 감지..." 등 로그 메시지
}
