package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class InvalidTokenException extends SmateException {

    public static final SmateException EXCEPTION = new InvalidTokenException();

    private InvalidTokenException() {
        super(ErrorCode.INVALID_TOKEN);
    }
}
