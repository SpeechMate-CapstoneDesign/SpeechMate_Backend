package com.example.speechmate_backend.user.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OauthProvider {

    KAKAO("KAKAO");

    private String value;

}
