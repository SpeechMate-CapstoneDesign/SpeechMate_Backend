package com.example.core.common.exception;

import com.example.core.common.error.ErrorCode;

public class SpeechContentAlreadyExistException extends SmateException {
    public static final SmateException EXCEPTION = new SpeechContentAlreadyExistException();



    public SpeechContentAlreadyExistException() {
        super(ErrorCode.SPEECH_CONTENT_ALREADY_EXIST);
    }
}
