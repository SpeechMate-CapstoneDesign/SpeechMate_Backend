package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class ReturnZeroException extends SmateException {
    public static final SmateException EXCEPTION = new ReturnZeroException();

    public ReturnZeroException() {
        super(ErrorCode.RETURN_ZERO_EXCEPTION);
    }
}
