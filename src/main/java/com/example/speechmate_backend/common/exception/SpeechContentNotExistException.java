package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class SpeechContentNotExistException extends SmateException {
    public static final SmateException EXCEPTION = new SpeechContentNotExistException();



    public SpeechContentNotExistException() {
        super(ErrorCode.SPEECH_CONTENT_NOT_EXIST);
    }}
