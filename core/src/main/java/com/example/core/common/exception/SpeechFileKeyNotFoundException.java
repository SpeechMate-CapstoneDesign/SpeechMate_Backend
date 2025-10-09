package com.example.core.common.exception;

import com.example.core.common.error.ErrorCode;

public class SpeechFileKeyNotFoundException extends SmateException {
    public static final SmateException EXCEPTION = new SpeechFileKeyNotFoundException();



    public SpeechFileKeyNotFoundException() {
        super(ErrorCode.SPEECH_FILE_KEY_NOT_FOUND);
    }
}
