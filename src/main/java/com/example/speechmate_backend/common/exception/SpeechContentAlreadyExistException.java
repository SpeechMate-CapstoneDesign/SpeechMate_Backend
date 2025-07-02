package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;
import com.example.speechmate_backend.common.error.ErrorCodeIfs;

public class SpeechContentAlreadyExistException extends SmateException {
    public static final SmateException EXCEPTION = new SpeechContentAlreadyExistException();



    public SpeechContentAlreadyExistException() {
        super(ErrorCode.SPEECH_ANALYSIS_RESULT_ALREADY_EXIST);
    }
}
