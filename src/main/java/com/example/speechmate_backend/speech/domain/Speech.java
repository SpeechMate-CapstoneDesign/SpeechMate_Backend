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


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "analysis_result_id")
    private AnalysisResult analysisResult;


    public void setFileUrl(String fileUrl) {
        this.FileUrl = fileUrl;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setAnalysisResult(AnalysisResult analysisResult) {
        this.analysisResult = analysisResult;
        // analysisResult 쪽에도 speech를 설정하여 양방향 관계를 동기화
        analysisResult.setSpeech(this);
    }
}
