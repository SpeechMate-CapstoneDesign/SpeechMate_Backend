package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class FileTooLargeException extends SmateException {
    public static final SmateException EXCEPTION = new FileTooLargeException();

    public FileTooLargeException() {
        super(ErrorCode.FILE_TOO_LARGE);
    }

}
