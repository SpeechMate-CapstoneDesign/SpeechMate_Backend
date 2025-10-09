package com.example.core.common.exception;

import com.example.core.common.error.ErrorCodeIfs;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SmateException extends RuntimeException {
    private ErrorCodeIfs error;

    public ErrorCodeIfs getError() {
        return this.error;
    }
}
