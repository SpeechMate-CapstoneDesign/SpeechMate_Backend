package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class InvalidOauthProviderException extends SmateException {
    public static final SmateException EXCEPTION = new InvalidOauthProviderException();

    public InvalidOauthProviderException() {
        super(ErrorCode.OAUTH_PROVIDER_NOT_MATCH);
    }
}
