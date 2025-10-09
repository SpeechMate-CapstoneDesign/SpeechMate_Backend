package com.example.core.common.exception;

import com.example.core.common.error.ErrorCode;

public class InvalidTokenException extends SmateException {

    public static final SmateException EXCEPTION = new InvalidTokenException();

    private InvalidTokenException() {
        super(ErrorCode.INVALID_TOKEN);
    }
}
