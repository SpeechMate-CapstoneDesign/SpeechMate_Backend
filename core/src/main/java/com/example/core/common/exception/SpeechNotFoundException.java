package com.example.core.common.exception;

import com.example.core.common.error.ErrorCode;

public class SpeechNotFoundException extends SmateException {
  public static final SmateException EXCEPTION = new SpeechNotFoundException();


  public SpeechNotFoundException() {
    super(ErrorCode.SPEECH_NOT_FOUND);
  }
}
