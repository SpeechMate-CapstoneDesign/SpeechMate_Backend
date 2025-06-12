package com.example.speechmate_backend.speech.domain;

import jakarta.persistence.*;

@Entity
public class AnalysisResult {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(mappedBy = "analysisResult")
    private Speech speech;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String keywords;

    private String sentiment; // POSITIVE, NEUTRAL, NEGATIVE 등  감정 분석

    @Column(columnDefinition = "TEXT")
    private String tone;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    private Integer structureScore;
    private Integer clarityScore;
    private Integer coherenceScore;

    private String aiModel; // "gpt-4", "claude", "openai-embedding", 등

}
