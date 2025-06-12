package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class UserNotFoundException extends SmateException {
  public static final SmateException EXCEPTION = new UserNotFoundException();


  public UserNotFoundException() {
    super(ErrorCode.USER_NOT_FOUND);
  }
}
