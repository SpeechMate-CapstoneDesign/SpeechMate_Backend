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
}
