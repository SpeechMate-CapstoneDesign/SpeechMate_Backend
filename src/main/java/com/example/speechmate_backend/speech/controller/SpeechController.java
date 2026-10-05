package com.example.speechmate_backend.speech.controller;

import com.example.speechmate_backend.common.ApiResponse;
import com.example.speechmate_backend.config.security.CustomUserDetails;
import com.example.speechmate_backend.s3.MediaFileExtension;
import com.example.speechmate_backend.s3.controller.dto.VoiceKeyDto;
import com.example.speechmate_backend.speech.controller.dto.*;
import com.example.speechmate_backend.speech.service.SpeechService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "스피치 분석", description = "스피치 파일 업로드 및 분석")
@RequestMapping("/api/speech")
@RequiredArgsConstructor
@RestController
public class SpeechController {

    private final SpeechService speechService;

    @Operation(summary = "1. s3용 presigned url 발급", description ="요청 후에 나온 url에다가 put 메소드로 파일 업로드하면 됩니다")
    @PostMapping("/presignedWithS3")
    public ResponseEntity<ApiResponse<VoiceKeyDto>> createSpeechAndGetPresignedUrlS3(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestParam MediaFileExtension fileExtension
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.createPresignedUrlS3(customUserDetails.getUserId(), fileExtension)));
    }

    @Operation(summary = "3. 텍스트 분석 open api", description = "stt로 변환된 content가 있어야 동작합니다.")
    @PostMapping("/analyze/{speechId}")
    public ResponseEntity<ApiResponse<AnalysisResultDto>> analyzeSpeech(
            @PathVariable Long speechId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        AnalysisResultDto dto = speechService.analyze(speechId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @Operation(summary = "2. STT 접수/조회", description = "처음 호출하면 STT를 접수하고 sttStatus=IN_PROGRESS를 돌려줍니다. "
            + "같은 요청을 다시 보내면 진행 중엔 상태만, 끝나면 COMPLETED와 sentences를 돌려줍니다. "
            + "FAILED는 retry=true(기본)면 다시 접수되고, 폴링 중에는 retry=false로 보내 FAILED를 그대로 받으세요.")
    @PostMapping(value = "/rtzrstt/{speechId}")
    public ResponseEntity<ApiResponse<SttGateResponse>> transcribesRtzr(
            @Parameter(description = "stt변환을 진행할 speechId", required = true)
            @PathVariable Long speechId,
            @RequestParam(defaultValue = "true") boolean retry,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.rtzrStt(speechId, userDetails.getUserId(), retry)));
    }

    @Operation(summary = "1-1. 업로드 완료 콜백", description = "클라이언트가 presigned url로 업로드 완료한 후 콜백 합니다.")
    @PostMapping("/s3-callback")
    public ResponseEntity<ApiResponse<SpeechS3CallbackDto>> callbackAfterUpload(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestParam String fileKey,
            @RequestParam Long durationSeconds
    ) {
        SpeechS3CallbackDto speechId = speechService.registerUploadedSpeech(customUserDetails.getUserId(), fileKey, durationSeconds);
        return ResponseEntity.ok(ApiResponse.ok(speechId));
    }

    @Operation(summary = "분석된 speech 조회", description = "클라이언트가 분석된 스피치들을 조회합니다.")
    @GetMapping("/mineAnalyzed")
    public ResponseEntity<ApiResponse<SpeechPagingResponseDto>> getSpeeches(
            @RequestParam(required = false) Long lastSpeechId,
            @RequestParam(defaultValue = "5") int limit,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId(); // 인증 유저 ID
        SpeechPagingResponseDto response = speechService.getAnalyzedSpeeches(userId, lastSpeechId, limit);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "모든 speech 조회", description = "클라이언트가 모든 스피치를 조회합니다.")
    @GetMapping("/mineAll")
    public ResponseEntity<ApiResponse<SpeechPagingResponseDto>> getAllSpeeches(
            @RequestParam(required = false) Long lastSpeechId,
            @RequestParam(defaultValue = "5") int limit,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        SpeechPagingResponseDto response = speechService.getAllSpeeches(userDetails.getUserId(),lastSpeechId, limit);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "스피치 메타데이터 추가", description = "발급받은 speechId에 해당하는 스피치에 발표 정보를 추가합니다.")
    @PutMapping("/metadata/{speechId}")
    public ResponseEntity<ApiResponse<SpeechIdDto>> addMetadata(
            @PathVariable Long speechId,
            @Valid @RequestBody SpeechMetadataRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.addMetadataToSpeech(speechId, requestDto, userDetails.getUserId())));
    }

    @Operation(summary = "단일 스피치 조회")
    @GetMapping("/{speechId}")
    public ResponseEntity<ApiResponse<SpeechResultDto>> getSpeechById(
            @PathVariable Long speechId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.getSpeechById(speechId, userDetails.getUserId())));
    }

    @Operation(summary = "파일과 관련된 정보를 불러옵니다.")
    @GetMapping("/{speechId}/speechConfig")
    public ResponseEntity<ApiResponse<SpeechConfigDto>> getSpeechConfigById(
            @PathVariable Long speechId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.getSpeechConfigById(speechId, userDetails.getUserId())));
    }

    @Operation(summary = "파일의 대본을 불러옵니다.")
    @GetMapping("/{speechId}/content")
    public ResponseEntity<ApiResponse<SpeechContentResponse>> getSpeechContnetById(
            @PathVariable Long speechId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.getSpeechContentById(speechId, userDetails.getUserId())));
    }

    @Operation(summary = "파일 대본 분석 결과를 불러옵니다.")
    @GetMapping("/{speechId}/contentAnalysis")
    public ResponseEntity<ApiResponse<AnalysisResultDto>> getSpeechContentAnalysisById(
            @PathVariable Long speechId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.getSpeechContentAnalysisById(speechId, userDetails.getUserId())));
    }

    @Operation(summary = "언어적 분석 결과를 불러옵니다.")
    @GetMapping("/{speechId}/verbalAnalysis")
    public ResponseEntity<ApiResponse<VerbalAnalysisDto>> getSpeechVerbalAnalysisById(
            @PathVariable Long speechId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.getSpeechVerbalAnalysisById(speechId, userDetails.getUserId())));
    }

    @Operation(summary = "피드를 조회합니다.")
    @GetMapping("/myFeed")
    public ResponseEntity<ApiResponse<SpeechPagingFeedDto>> getSpeechContentAnalysisById(
            @RequestParam(required = false) Long lastSpeechId,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "LATEST") SortType sortType, // LATEST, OLDEST, NAME
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        SpeechPagingFeedDto dto = speechService.getMySpeecheFeed(userDetails.getUserId(), lastSpeechId, limit, sortType);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @Operation(summary = "스피치를 삭제합니다.")
    @DeleteMapping("/delete/{speechId}")
    public ResponseEntity<String> deleteSpeechById(
        @PathVariable Long speechId,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        speechService.deleteSpeechById(speechId, userId);

        return ResponseEntity.ok("삭제 완료");
    }


    @Operation(summary = "파이썬 서버와 비언어적 분석 통신")
    @PostMapping("/nonverbal/{speechId}")
    public ResponseEntity<ApiResponse<NonVerbalAnalysisGateResponse>> nonverbalcall(
            @PathVariable Long speechId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.requestNonVerbalAnalysis(speechId, userDetails.getUserId())));
    }
}
