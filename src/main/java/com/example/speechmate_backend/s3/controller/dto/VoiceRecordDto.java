package com.example.speechmate_backend.s3.controller.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
public record VoiceRecordDto(
        String url, // 클라이언트가 업로드할 S3 URL
        String key // S3에 저장될 파일 키 (서버에서 추적할 때 사용)

) {

    public static VoiceRecordDto of(String url, String key) {
        return VoiceRecordDto.builder().key(key).url(url).build();
    }
}
