package com.example.core.common.exception;

import com.example.core.common.error.ErrorCode;

public class FileTooLargeException extends SmateException {
    public static final SmateException EXCEPTION = new FileTooLargeException();

    public FileTooLargeException() {
        super(ErrorCode.FILE_TOO_LARGE);
    }

}
