package com.example.speechmate_backend.speech.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EventDetailDto {
    private String name;
    private int count;
    private List<String> timestamps; // 타임스탬프는 00:00 String형식
}
