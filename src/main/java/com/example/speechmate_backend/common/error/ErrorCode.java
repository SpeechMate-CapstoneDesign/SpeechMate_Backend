package com.example.speechmate_backend.common.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum ErrorCode implements ErrorCodeIfs{

    INVALID_TOKEN("fail", HttpStatus.SC_BAD_REQUEST,"잘못된 토큰, 재 로그인해주세요"),
    TOKEN_EXPIRED("fail", HttpStatus.SC_BAD_REQUEST, "만료된 토큰"),
    OAUTH_PROVIDER_NOT_MATCH("fail", HttpStatus.SC_BAD_REQUEST, "provider가 올바르지 않음"),
    USER_ALREADY_EXIST_INFO("fail", HttpStatus.SC_BAD_REQUEST, "이미 존재하는 Oauth 정보"),
    SPEECH_NOT_FOUND("fail", HttpStatus.SC_NOT_FOUND, "존재하지 않는 스피치"),
    USER_NOT_FOUND("fail", HttpStatus.SC_NOT_FOUND, "존재하지 않는 유저"),
    SPEECH_CONTENT_ALREADY_EXIST("fail", HttpStatus.SC_CONFLICT, "stt로 변환된 content가 이미 존재."),
    SPEECH_CONTENT_NOT_EXIST("fail", HttpStatus.SC_CONFLICT, "stt로 변환된 content가 존재X"),
    SPEECH_ANALYSIS_RESULT_ALREADY_EXIST("fail", HttpStatus.SC_CONFLICT, "ai로 분석된 analysisREsult가 이미 존재."),
    ;


    private final String status;
    private final Integer resultCode;
    private final String message;
}
