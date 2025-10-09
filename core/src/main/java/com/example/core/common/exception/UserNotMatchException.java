package com.example.core.common.exception;

import com.example.core.common.error.ErrorCode;

public class UserNotMatchException extends SmateException {
  public static final SmateException EXCEPTION = new UserNotMatchException();


  public UserNotMatchException() {
    super(ErrorCode.USER_NOT_MATCH);
  }
}
