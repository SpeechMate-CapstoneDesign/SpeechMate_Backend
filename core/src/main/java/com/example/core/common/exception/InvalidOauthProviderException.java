package com.example.core.common.exception;

import com.example.core.common.error.ErrorCode;

public class InvalidOauthProviderException extends SmateException {
    public static final SmateException EXCEPTION = new InvalidOauthProviderException();

    public InvalidOauthProviderException() {
        super(ErrorCode.OAUTH_PROVIDER_NOT_MATCH);
    }
}
