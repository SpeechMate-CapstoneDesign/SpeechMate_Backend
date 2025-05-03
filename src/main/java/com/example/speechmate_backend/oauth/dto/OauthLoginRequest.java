package com.example.speechmate_backend.oauth.dto;

import lombok.Data;

@Data
public class OauthLoginRequest {
    private String idToken;
    private String provider;
}
