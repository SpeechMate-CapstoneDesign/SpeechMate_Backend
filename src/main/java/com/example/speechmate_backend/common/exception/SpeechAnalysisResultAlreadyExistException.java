package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class SpeechAnalysisResultAlreadyExistException extends SmateException {
    public static final SmateException EXCEPTION = new SpeechAnalysisResultAlreadyExistException();



    public SpeechAnalysisResultAlreadyExistException() {
        super(ErrorCode.SPEECH_CONTENT_ALREADY_EXIST);
    }
}
