package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCodeIfs;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class SmateException extends RuntimeException {
    private ErrorCodeIfs error;

    public ErrorCodeIfs getError() {
        return this.error;
    }
}
