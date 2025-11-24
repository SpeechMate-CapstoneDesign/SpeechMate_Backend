package com.example.speechmate_backend.speech.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NonVerbalAnalysisRequest {

    private Long speechId;
    // Python 서버로 보낼 S3 파일 키
    private String s3FileKey;

}
