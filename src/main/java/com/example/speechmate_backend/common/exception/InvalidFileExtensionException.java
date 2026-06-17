package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class InvalidFileExtensionException extends SmateException {
    public static final SmateException EXCEPTION = new InvalidFileExtensionException();

    public InvalidFileExtensionException() {
        super(ErrorCode.INVALID_FILE_EXTENSION);
    }
}
