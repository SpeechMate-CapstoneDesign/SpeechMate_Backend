package com.example.core.common.exception;

import com.example.core.common.error.ErrorCode;

public class ExpiredTokenException extends SmateException {
  public static final SmateException EXCEPTION = new ExpiredTokenException();

  private ExpiredTokenException() {
    super(ErrorCode.TOKEN_EXPIRED);
  }

}

