package com.example.speechmate_backend.speech.controller;

import com.example.speechmate_backend.common.ApiResponse;
import com.example.speechmate_backend.config.security.CustomUserDetails;
import com.example.speechmate_backend.s3.MediaFileExtension;
import com.example.speechmate_backend.s3.controller.dto.VoiceRecordDto;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.controller.dto.SpeechContentRequest;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.speech.service.SpeechService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/speech")
@RequiredArgsConstructor
@RestController
public class SpeechController {

    private final SpeechRepository speechRepository;
    private final S3UploadPresignedUrlService s3UploadPresignedUrlService;
    private final SpeechService speechService;

    // 1. Speech 생성 + presigned URL 발급
    @PostMapping("/presigned")
    public ResponseEntity<ApiResponse<VoiceRecordDto>> createSpeechAndGetPresignedUrl(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestParam MediaFileExtension fileExtension
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.createPresignedUrl(customUserDetails.getUserId(), fileExtension)));
    }


    //stt결과를 받아오는
    @PostMapping("/{speechId}/content")
    public ResponseEntity<ApiResponse<Long>> getContent(
            @PathVariable Long speechId,
            @RequestBody @Valid SpeechContentRequest request
    ) {
        speechService.getContent(speechId, request);
        return ResponseEntity.ok(ApiResponse.ok(speechId));
    }




}
