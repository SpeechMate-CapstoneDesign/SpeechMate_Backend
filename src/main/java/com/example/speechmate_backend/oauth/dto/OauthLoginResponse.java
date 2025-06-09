package com.example.speechmate_backend.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@Data
public class OauthLoginResponse {

    private boolean isNewUser;
    private String access;
    private String refresh;
    private String accessExpiredAt;
    private String refreshExpiredAt;
}
