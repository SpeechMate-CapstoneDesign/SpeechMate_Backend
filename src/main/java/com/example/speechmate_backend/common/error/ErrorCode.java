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
    SPEECH_ANALYSIS_RESULT_ALREADY_EXIST("fail", HttpStatus.SC_CONFLICT, "ai로 분석된 analysisResult가 이미 존재."),
    SPEECH_FILE_KEY_DOES_NOT_EQUAL("fail", HttpStatus.SC_BAD_REQUEST, "파일 키가 저장된 것과 일치하지 않음"),
    SPEECH_FILE_KEY_NOT_FOUND("fail", HttpStatus.SC_NOT_FOUND, "파일키가 없음"),
    WHISPER_EXCEPTION("fail", HttpStatus.SC_INTERNAL_SERVER_ERROR, "stt를 받아오는 whisper과정에서 오류 발생"),
    USER_NOT_MATCH("fail", HttpStatus.SC_FORBIDDEN, "현재 로그인 한 유저가 일치하지 않습니다."),
    FILE_TOO_LARGE("fail", HttpStatus.SC_BAD_REQUEST, "파일 크기가 25MB가 넘어갑니다. 작은 파일을 업로드해주세요"),
    FFMPEG_EXCEPTION("fail", HttpStatus.SC_INTERNAL_SERVER_ERROR, "파일 변환 중 오류 발생(ffmpeg)"),
    RETURN_ZERO_EXCEPTION("fail", HttpStatus.SC_INTERNAL_SERVER_ERROR, "ReturnZero stt 과정중 오류 발생"),
    UPLOAD_LIMIT_EXCEED("fail", HttpStatus.SC_TOO_MANY_REQUESTS, "이미 5회 업로드 했습니다."),
    INVALID_FILE_EXTENSION("fail", HttpStatus.SC_BAD_REQUEST, "허용되지 않는 파일 확장자입니다.");

    private final String status;
    private final Integer resultCode;
    private final String message;
}
