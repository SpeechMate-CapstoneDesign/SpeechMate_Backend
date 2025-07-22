package com.example.speechmate_backend.speech.controller;

import com.example.speechmate_backend.common.ApiResponse;
import com.example.speechmate_backend.config.security.CustomUserDetails;
import com.example.speechmate_backend.s3.MediaFileExtension;
import com.example.speechmate_backend.s3.controller.dto.VoiceKeyDto;
import com.example.speechmate_backend.s3.controller.dto.VoiceRecordDto;
import com.example.speechmate_backend.speech.controller.dto.SpeechIdDto;
import com.example.speechmate_backend.speech.controller.dto.SpeechResultDto;
import com.example.speechmate_backend.speech.service.SpeechService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "스피치 분석", description = "스피치 파일 업로드 및 분석")
@RequestMapping("/api/speech")
@RequiredArgsConstructor
@RestController
public class SpeechController {

    private final SpeechService speechService;
    private final SpeechRestClient speechRestClient;

    // 1. Speech 생성 + presigned URL 발급(gcs)
    /*@Operation(summary = "gcs용 presigned url 발급(사용X)")
    @PostMapping("/presignedWithGcs")
    public ResponseEntity<ApiResponse<VoiceRecordDto>> createSpeechAndGetPresignedUrlGcs(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestParam MediaFileExtension fileExtension
    ) {
        return ResponseEntity.ok(ApiResponse.ok(speechService.createPresignedUrlGcp(customUserDetails.getUserId(), fileExtension)));
    }*/

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
    public ResponseEntity<ApiResponse<SpeechResultDto>> analyzeSpeech(
            @PathVariable Long speechId
    ) {
        SpeechResultDto dto = speechService.analyze(speechId);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }


    @Operation(summary = "2-1. whisper api Multipart용", description = "multipart 파일로부터 stt로변환된 내용을 뽑아냅니다.(1을 먼저 선행하여 s3에 파일 저장후 요청해주세요")
    @PostMapping(value = "/Whisperstt2/{speechId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<String>> transcribe2(
            @Parameter(description = "업로드할 음성 파일", required = true, content = @Content(mediaType = "multipart/form-data"))
            @RequestParam("file") MultipartFile file,
            @PathVariable Long speechId) {
        return speechService.transcribeversion2(file, speechId);
    }

    @Operation(summary = "1-1. 업로드 완료 콜백", description = "클라이언트가 presigned url로 업로드 완료한 후 콜백 합니다.")
    @PostMapping("/s3-callback")
    public ResponseEntity<ApiResponse<SpeechIdDto>> callbackAfterUpload(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestParam String fileKey
    ) {
        SpeechIdDto speechId = speechService.registerUploadedSpeech(customUserDetails.getUserId(), fileKey);
        return ResponseEntity.ok(ApiResponse.ok(speechId));
    }



   /* @PostMapping("/{speechId}/transcribeWithWhisper")
    public ResponseEntity<ApiResponse<Long>> transcribeSpeechWithWhisper(
            @PathVariable Long speechId
    ) {
        speechService.transcribeWithWhisper(speechId);
        return ResponseEntity.ok(ApiResponse.ok(speechId));
    }*/

    // s3 presigned url로 파일 올린 후  안되서 임시로 만든 upload
    /*@PostMapping("/upload/{speechId}")
    public ResponseEntity<String> testUploadWhisper(
            @PathVariable Long speechId,
            @RequestPart("file") MultipartFile file

    ) {
        //String result = speechService.transcribeWithMultipartFile(file, speechId);
        String result = speechService.callWhisperStt(file, speechId);

        return ResponseEntity.ok(result);
    }*/





    /*@PostMapping("/{speechId}/transcribe")
    public ResponseEntity<ApiResponse<Long>> transcribeSpeechWithGoogle(
            @PathVariable Long speechId
    ) {
        speechService.transcribeWithGoogle(speechId);
        return ResponseEntity.ok(ApiResponse.ok(speechId));
    }*/



}
