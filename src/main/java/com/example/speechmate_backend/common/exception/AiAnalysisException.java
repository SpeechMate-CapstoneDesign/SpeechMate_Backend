package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class AiAnalysisException extends SmateException {
    public static final SmateException EXCEPTION = new AiAnalysisException();

    public AiAnalysisException() {
        super(ErrorCode.AI_ANALYSIS_EXCEPTION);
    }
}
