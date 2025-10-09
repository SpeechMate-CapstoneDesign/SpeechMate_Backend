package com.example.core.speech.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Silence {
    private long duration; //총 침묵 시간
    private long startTime; //침묵 시작 시간
    private long endTime; //침묵
    private String wordBefore;
    private String wordAfter;
}
