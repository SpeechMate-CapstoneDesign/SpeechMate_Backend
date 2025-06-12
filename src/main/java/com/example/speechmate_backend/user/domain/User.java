package com.example.speechmate_backend.user.domain;

import com.example.speechmate_backend.common.BaseEntity;
import com.example.speechmate_backend.speech.domain.Speech;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Speech> speechs = new ArrayList<>();

    @Embedded
    private OauthInfo oauthInfo;

    public void addSpeech(Speech speech) {
        this.speechs.add(speech);
        if(speech.getUser() != this) {
            speech.setUser(this);
        }
    }

}
