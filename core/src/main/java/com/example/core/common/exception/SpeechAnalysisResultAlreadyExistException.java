package com.example.core.common.exception;

import com.example.core.common.error.ErrorCode;

public class SpeechAnalysisResultAlreadyExistException extends SmateException {
    public static final SmateException EXCEPTION = new SpeechAnalysisResultAlreadyExistException();



    public SpeechAnalysisResultAlreadyExistException() {
        super(ErrorCode.SPEECH_CONTENT_ALREADY_EXIST);
    }
}
