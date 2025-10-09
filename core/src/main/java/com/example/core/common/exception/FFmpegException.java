package com.example.core.common.exception;

import com.example.core.common.error.ErrorCode;

public class FFmpegException extends SmateException {
    public static final SmateException EXECPTION = new FFmpegException();

    public FFmpegException() {
        super(ErrorCode.FFMPEG_EXCEPTION);
    }
}
