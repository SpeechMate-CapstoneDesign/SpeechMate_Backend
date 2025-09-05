package com.example.speechmate_backend.speech.domain;

import com.example.speechmate_backend.speech.controller.dto.AnalysisResultDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
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

    @ElementCollection
    @CollectionTable(name = "analysis_improvement_points", joinColumns = @JoinColumn(name = "analysis_result_id"))
    @Column(name = "point")
    private List<String> improvementPoints;


    @Column(columnDefinition = "TEXT")
    private String feedback;

    @ElementCollection
    @CollectionTable(name = "analysis_expected_questions", joinColumns = @JoinColumn(name = "analysis_result_id"))
    @Column(name = "question")
    private List<String> expectedQuestions;


    public void setSpeech(Speech speech) {
        this.speech = speech;
    }

    public static AnalysisResult from(AnalysisResultDto dto) {
        return AnalysisResult.builder()
                .summary(dto.summary())
                .keywords(dto.keywords())
                .improvementPoints(dto.improvementPoints())
                .expectedQuestions(dto.expectedQuestions())
                .feedback(dto.feedback())
                .build();
    }
}
