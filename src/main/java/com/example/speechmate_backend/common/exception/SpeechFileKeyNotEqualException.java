package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class SpeechFileKeyNotEqualException extends SmateException {
    public static final SmateException EXCEPTION = new SpeechFileKeyNotEqualException();



    public SpeechFileKeyNotEqualException() {
        super(ErrorCode.SPEECH_CONTENT_ALREADY_EXIST);
    }
}
