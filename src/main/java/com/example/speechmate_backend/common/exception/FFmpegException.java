package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.error.ErrorCode;

public class FFmpegException extends SmateException {
    public static final SmateException EXECPTION = new FFmpegException();

    public FFmpegException() {
        super(ErrorCode.FFMPEG_EXCEPTION);
    }
}
