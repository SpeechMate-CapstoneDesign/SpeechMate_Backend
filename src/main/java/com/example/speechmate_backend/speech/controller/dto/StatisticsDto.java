package com.example.speechmate_backend.speech.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsDto {
    // --- 기존 Head/Face/Slant ---
    private int totalFloorEvents;
    private int totalCeilingEvents;
    //private int totalLeftEvents;
    //private int totalRightEvents;
    private int totalSlantEvents;
    private int totalBlinkEvents;
    //경직된 차려자세

    // --- 기존 Hand/Mouth ---
    private int totalLipBiteEvents;
    private int totalHandNearFaceEvents; // (손/턱,코,귀,입술,이마,머리 만지기)의 총합으로 사용 가능

    // ----------------------------------------------------
    //  [신규 추가 필드 - 목록 반영]
    // ----------------------------------------------------
    private int totalArmsCrossedEvents;   // 팔/팔짱 끼기
    private int totalHandsBehindBackEvents; // 팔/뒷짐
    private int totalHandsRubbingEvents;    // 손/손 비비기
    private int totalFigLeafPoseEvents;   // 자세/무화과 잎 자세
}
