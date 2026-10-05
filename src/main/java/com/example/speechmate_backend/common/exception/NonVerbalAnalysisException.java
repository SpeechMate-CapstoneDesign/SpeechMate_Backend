package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class NonVerbalAnalysisException extends SmateException {
    public static final SmateException EXCEPTION = new NonVerbalAnalysisException();

    public NonVerbalAnalysisException() {
        super(ErrorCode.NON_VERBAL_ANALYSIS_EXCEPTION);
    }
}
