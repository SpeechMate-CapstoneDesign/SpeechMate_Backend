package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class UploadLimitExceededException extends SmateException {
  public static final SmateException EXCEPTION = new UploadLimitExceededException();

    public UploadLimitExceededException() {
        super(ErrorCode.UPLOAD_LIMIT_EXCEED);
    }
}
