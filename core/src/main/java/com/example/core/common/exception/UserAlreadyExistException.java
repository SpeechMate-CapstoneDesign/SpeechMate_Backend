package com.example.core.common.exception;

import com.example.core.common.error.ErrorCode;

public class UserAlreadyExistException extends SmateException {
  public static final SmateException EXCEPTION = new UserAlreadyExistException();


  public UserAlreadyExistException() {
    super(ErrorCode.USER_ALREADY_EXIST_INFO);
  }
}
