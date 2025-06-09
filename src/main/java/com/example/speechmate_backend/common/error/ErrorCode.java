package com.example.speechmate_backend.common.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ErrorCode implements ErrorCodeIfs{

    INVALID_TOKEN("fail", 101,"잘못된 토큰, 재 로그인해주세요"),
    TOKEN_EXPIRED("fail", 102, "만료된 토큰"),
    OAUTH_PROVIDER_NOT_MATCH("fail", 103, "provider가 올바르지 않음"),
    USER_ALREADY_EXIST_INFO("fail", 104, "이미 존재하는 Oauth 정보"),
    ;


    private final String status;
    private final Integer resultCode;
    private final String message;
}
