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
    private final SpeechRestClient speechRestClient;


    @Operation(summary = "1. s3용 presigned url 발급", description ="요청 후에 나온 url에다가 put 메소드로 파일 업로드하면 됩니다")
    @PostMapping("/presignedWithS3")
    public ResponseEntity<ApiResponse<VoiceKeyDto>> createSpeechAndGetPresignedUrlS3(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestParam MediaFileExtension fileExtension
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.createPresignedUrlS3(customUserDetails.getUserId(), fileExtension)));
    }

    /*@Operation(summary = "2. whisper 이용 text추출 api", description = "파일로부터 stt로변환된 내용을 뽑아냅니다.(1을 먼저 선행하여 s3에 파일 저장후 요청해주세요")
    @PostMapping(value = "/Whisperstt/{speechId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<String>> transcribe(
            @Parameter(description = "업로드할 음성 파일", required = true, content = @Content(mediaType = "multipart/form-data"))
            @RequestParam("file") MultipartFile file, @PathVariable Long speechId) {
        return speechService.callWhisperStt(file, speechId);
    }*/

    //stt결과로 AI 분석까지.
    @Operation(summary = "3. 텍스트 분석 open api", description = "stt로 변환된 content가 있어야 동작합니다.")
    @PostMapping("/analyze/{speechId}")
    public ResponseEntity<ApiResponse<AnalysisResultDto>> analyzeSpeech(
            @PathVariable Long speechId
    ) {
        AnalysisResultDto dto = speechService.analyze(speechId);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }


//    @Operation(summary = "2-1. whisper api Multipart용", description = "multipart 파일로부터 stt로변환된 내용을 뽑아냅니다.(1을 먼저 선행하여 s3에 파일 저장후 요청해주세요")
//    @PostMapping(value = "/Whisperstt2/{speechId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity<ApiResponse<String>> transcribe2(
//            @Parameter(description = "업로드할 음성 파일", required = true, content = @Content(mediaType = "multipart/form-data"))
//            @RequestParam("file") MultipartFile file,
//            @PathVariable Long speechId) {
//        return speechService.transcribeversion2(file, speechId);
//    }

    @Operation(summary = "2-1. whisper api s3에서 받아온것", description = "저장된 s3 파일로부터 stt로변환된 내용을 뽑아냅니다.(1을 먼저 선행하여 s3에 파일 저장후 요청해주세요")
    @PostMapping(value = "/Whisperstt3/{speechId}")
    public ResponseEntity<ApiResponse<SpeechContentResponse>> transcribes3(
            @Parameter(description = "stt변환을 진행할 speechId", required = true)
            @PathVariable Long speechId) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.transcribeversionFromS3(speechId)));
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
            @PathVariable Long speechId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.getSpeechById(speechId)));
    }

    @GetMapping("/{speechId}/speechConfig")
    public ResponseEntity<ApiResponse<SpeechConfigDto>> getSpeechConfigById(
            @PathVariable Long speechId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.getSpeechConfigById(speechId)));
    }

    @GetMapping("/{speechId}/content")
    public ResponseEntity<ApiResponse<SpeechContentResponse>> getSpeechContnetById(
            @PathVariable Long speechId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.getSpeechContentById(speechId)));
    }

    @GetMapping("/{speechId}/contentAnalysis")
    public ResponseEntity<ApiResponse<AnalysisResultDto>> getSpeechContentAnalysisById(
            @PathVariable Long speechId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.getSpeechContentAnalysisById(speechId)));
    }

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

    @DeleteMapping("/delete/{speechId}")
    public ResponseEntity<String> deleteSpeechById(
        @PathVariable Long speechId,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        speechService.deleteSpeechById(speechId, userId);

        return ResponseEntity.ok("삭제 완료");
    }

}
