package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class UserAlreadyExistException extends SmateException {
  public static final SmateException EXCEPTION = new UserAlreadyExistException();


  public UserAlreadyExistException() {
    super(ErrorCode.USER_ALREADY_EXIST_INFO);
  }
}
