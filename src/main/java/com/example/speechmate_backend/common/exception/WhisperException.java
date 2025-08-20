package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;


public class WhisperException extends SmateException {
    public static final SmateException EXCEPTION = new WhisperException();


    public WhisperException() {
        super(ErrorCode.WHISPER_EXCEPTION);
    }
}