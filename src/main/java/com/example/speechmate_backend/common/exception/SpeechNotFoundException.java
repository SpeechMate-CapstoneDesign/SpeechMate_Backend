package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class SpeechNotFoundException extends SmateException {
  public static final SmateException EXCEPTION = new SpeechNotFoundException();


  public SpeechNotFoundException() {
    super(ErrorCode.SPEECH_NOT_FOUND);
  }
}
