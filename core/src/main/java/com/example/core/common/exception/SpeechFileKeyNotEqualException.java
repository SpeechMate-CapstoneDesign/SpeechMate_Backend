package com.example.core.common.exception;

import com.example.core.common.error.ErrorCode;

public class SpeechFileKeyNotEqualException extends SmateException {
    public static final SmateException EXCEPTION = new SpeechFileKeyNotEqualException();



    public SpeechFileKeyNotEqualException() {
        super(ErrorCode.SPEECH_FILE_KEY_DOES_NOT_EQUAL);
    }
}
