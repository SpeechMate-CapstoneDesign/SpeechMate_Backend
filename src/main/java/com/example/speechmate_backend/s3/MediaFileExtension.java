package com.example.speechmate_backend.s3;

import lombok.Getter;

@Getter
public enum MediaFileExtension {
    WAV("audio/wav"),
    MP3("audio/mpeg"),
    MP4("video/mp4"),
    MOV("video/quicktime"),
    M4A("audio/mp4");

    private final String mimeType;

    public String getUploadExtension() {
        return this.name().toLowerCase();
    }

    MediaFileExtension(String mimeType) {
        this.mimeType = mimeType;
    }
}
