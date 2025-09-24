package com.example.speechmate_backend.speech.domain;


import com.example.speechmate_backend.common.BaseEntity;
import com.example.speechmate_backend.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
public class Speech extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String FileUrl;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;  //stt변환 결과

    private String title; // 발표 파일 이름

    private String presentationContext; // 발표 상황

    private String audience; // 청중

    private String location; // 장소

    private Long duration; // 파일 재생 시간 (초 단위)
    private String fileType; // 파일 타입(m4a, wav면 audio, mp4면 video)

    public void updateMediaInfo(Long duration, String fileType) {
        this.duration = duration;
        this.fileType = fileType;
    }

    public void updateMetadata(String title, String presentationContext, String audience, String location) {
        this.title = title;
        this.presentationContext = presentationContext;
        this.audience = audience;
        this.location = location;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "analysis_result_id")
    private AnalysisResult analysisResult;

    @OneToOne(mappedBy = "speech", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private VerbalAnalysisResult verbalAnalysisResult;



    public void setFileUrl(String fileUrl) {
        this.FileUrl = fileUrl;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setDuration(Long duration) {this.duration = duration; };

    public void setFileType(String fileType) {this.fileType = fileType; };

    public void setAnalysisResult(AnalysisResult analysisResult) {
        this.analysisResult = analysisResult;
        // analysisResult 쪽에도 speech를 설정하여 양방향 관계를 동기화
        analysisResult.setSpeech(this);
    }

    public void setVerbalAnalysisResult(VerbalAnalysisResult verbalAnalysisResult) {
        this.verbalAnalysisResult = verbalAnalysisResult;
        if (verbalAnalysisResult != null) {
            verbalAnalysisResult.setSpeech(this); // 양방향 동기화
        }
    }
}
