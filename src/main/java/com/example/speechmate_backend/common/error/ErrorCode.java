package com.example.speechmate_backend.common.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ErrorCode implements ErrorCodeIfs{

    INVALID_TOKEN("fail", 401,"잘못된 토큰, 재 로그인해주세요"),
    TOKEN_EXPIRED("fail", 401, "만료된 토큰");

    private final String status;
    private final Integer resultCode;
    private final String message;
}
