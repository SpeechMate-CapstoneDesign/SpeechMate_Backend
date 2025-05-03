package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class InvalidTokenException extends SmateExeption {

    public static final SmateExeption EXEPTION = new InvalidTokenException();

    private InvalidTokenException() {
        super(ErrorCode.INVALID_TOKEN);
    }
}
