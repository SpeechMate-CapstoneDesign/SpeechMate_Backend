package com.example.core.common.exception;

import com.example.core.common.error.ErrorCode;


public class WhisperException extends SmateException {
    public static final SmateException EXCEPTION = new WhisperException();


    public WhisperException() {
        super(ErrorCode.WHISPER_EXCEPTION);
    }
}