package com.example.speechmate_backend.speech.domain;

import jakarta.persistence.*;
import lombok.Setter;

@Setter
@Entity
@Table(name = "verbal_analysis_result")
public class VerbalAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "speech_id", nullable = false)
    private Speech speech;

    private long wordCnt;
    private long syllableCnt;

    @Lob
    private String silenceJson; // 침묵 구간 JSON으로 직렬화해서 저장
    @Lob
    private String fillerJson;  // 간투어 발생 시간 JSON
    @Lob
    private String repeatedWordsJson; // 반복 단어/횟수 JSON

    // getter, setter, constructor
}

