package com.example.core.common.exception;

import com.example.core.common.error.ErrorCode;

public class ReturnZeroException extends SmateException {
    public static final SmateException EXCEPTION = new ReturnZeroException();

    public ReturnZeroException() {
        super(ErrorCode.RETURN_ZERO_EXCEPTION);
    }
}
