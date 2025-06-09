package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class ExpiredTokenException extends SmateException {
  public static final SmateException EXCEPTION = new ExpiredTokenException();

  private ExpiredTokenException() {
    super(ErrorCode.TOKEN_EXPIRED);
  }

}

