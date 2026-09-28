package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class LockAcquisitionFailedException extends SmateException {
  public static final SmateException EXCEPTION = new LockAcquisitionFailedException();

  public LockAcquisitionFailedException() {
    super(ErrorCode.LOCK_ACQUISITION_FAILED);
  }
}
