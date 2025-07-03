package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.common.ApiResponse;
import com.example.speechmate_backend.common.exception.SpeechContentAlreadyExistException;
import com.example.speechmate_backend.common.exception.SpeechContentNotExistException;
import com.example.speechmate_backend.common.exception.SpeechNotFoundException;
import com.example.speechmate_backend.common.exception.UserNotFoundException;
import com.example.speechmate_backend.s3.MediaFileExtension;
import com.example.speechmate_backend.s3.controller.dto.VoiceRecordDto;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.controller.SpeechRestClient;
import com.example.speechmate_backend.speech.controller.dto.SpeechResultDto;
import com.example.speechmate_backend.speech.domain.AnalysisResult;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.user.domain.User;
import com.example.speechmate_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;




@Slf4j
@RequiredArgsConstructor
@Service
public class SpeechService {

    private final UserRepository userRepository;
    private final S3UploadPresignedUrlService s3UploadPresignedUrlService;
    private final SpeechRepository speechRepository;
    private final SpeechAnalysisResultService speechAnalysisResultService;
    private final SpeechRestClient speechRestClient;

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;
/*

    @Transactional
    public void transcribeWithGoogle(Long speechId) {
        Speech speech = speechRepository.findById(speechId)
                .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);

        if (speech.getContent() != null && !speech.getContent().isEmpty()) {
            return; // 이미 STT된 경우 종료
        }

        try {
            String transcript = googleSttService.transcribe(speech.getFileUrl());
            speech.setContent(transcript);
            speechRepository.save(speech);
            log.info("[STT 성공] Speech ID {} 원고 추출 완료", speechId);
        } catch (Exception e) {
            log.error("[STT 실패] Speech ID {} 원고 추출 실패: {}", speechId, e.getMessage(), e);
            // 원하면 STT 실패 시 따로 정의된 예외로 던질 수도 있음
            throw new IllegalStateException("STT 작업 실패: " + e.getMessage(), e);
        }
    }
*/

    @Transactional
    public SpeechResultDto analyze(Long speechId) {
        Speech speech = speechRepository.findById(speechId)
                .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);

        if (speech.getAnalysisResult() != null) {
            throw SpeechContentAlreadyExistException.EXCEPTION; // 이미 분석된 경우 종료
        }

        if (speech.getContent() == null || speech.getContent().isEmpty()) {
            throw SpeechContentNotExistException.EXCEPTION;
        }

        try {
            log.info("Speech ID {}에 대한 텍스트 분석을 시작합니다.", speechId);

            AnalysisResult result = speechAnalysisResultService.analyzeText(speech.getContent());
            speech.setAnalysisResult(result);
            speechRepository.save(speech);
            String fileUrl = s3UploadPresignedUrlService.getPublicS3Url(speech.getFileUrl());
            log.info("[AI 분석 성공] Speech ID {} 논리 점수: {}", speechId, result.getLogicalCoherenceScore());
            return SpeechResultDto.from(speech, fileUrl);
        } catch (Exception e) {
            log.error("[AI 분석 실패] Speech ID {}: {}", speechId, e.getMessage(), e);
            // 원하면 AI 실패 시 따로 정의된 예외로 던질 수도 있음
            throw new IllegalStateException("AI 분석 실패: " + e.getMessage(), e);
        }
    }


    /*public String transcribeWithMultipartFile(MultipartFile file, Long speechId) {
        Speech speech = speechRepository.findById(speechId)
                .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);
        log.info("🔍 MultipartFile 디버깅 시작");
        log.info("파일 이름: {}", file.getOriginalFilename());
        log.info("파일 크기: {} bytes", file.getSize());
        log.info("Content-Type: {}", file.getContentType());
        log.info("isEmpty: {}", file.isEmpty());
        log.info("파일 확장자: {}",
                file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")
                        ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."))
                        : "없음"
        );
        if (speech.getAnalysisResult() != null) {
            throw new IllegalStateException("이미 분석 결과가 있음 "); // 이미 분석된 경우 종료
        }


        try {

            String result = whisperClient.transcribe(
                    file,
                    "whisper-1",
                    "ko",
                    "text",
                    "Bearer " + openAiApiKey
            );
            log.info("Whisper STT 결과: {}", result);
            speech.setContent(result);
            return result;
        } catch (Exception e) {
            log.error("Whisper 호출 실패", e);
            throw new IllegalStateException("Whisper 호출 실패: " + e.getMessage(), e);
        }
    }*/

    public ResponseEntity<ApiResponse<String>> callWhisperStt(MultipartFile file, Long speechId) {
        Speech speech = speechRepository.findById(speechId)
                .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);
        if(speech.getContent() != null && !speech.getContent().isEmpty()) {
            throw SpeechContentAlreadyExistException.EXCEPTION;
        }
        try {
            String content = speechRestClient.transcribe(file.getResource());
            speech.setContent(content);
            speechRepository.save(speech);
            return ResponseEntity.ok(ApiResponse.ok("stt변환 성공"));
        } catch (Exception e) {
            throw new IllegalStateException("Whisper 호출 실패: " + e.getMessage(), e);
        }
    }




    @Transactional
    public VoiceRecordDto createPresignedUrlS3(Long userId, MediaFileExtension fileExtension) {
        // 1. Speech 객체 저장 (content, audioFileUrl은 아직 null)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.EXCEPTION);
        log.info("userID: " + userId);
        Speech speech = new Speech();

        user.addSpeech(speech);
        speechRepository.save(speech);// 이 시점에 speechId 생성
        log.info("speechId: " + speech.getId());

        // 2. Presigned URL 발급
        VoiceRecordDto dto = s3UploadPresignedUrlService.generatePreSignedUrlForSpeech(userId, speech.getId(), fileExtension);

        //Speech에 S3 Key 저장
        speech.setFileUrl(dto.key());

        speechRepository.save(speech);

        return dto;
    }

    /*@Transactional
    public VoiceRecordDto createPresignedUrlGcp(Long userId, MediaFileExtension fileExtension) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.EXCEPTION);
        Speech speech = new Speech();
        user.addSpeech(speech);
        speechRepository.save(speech);

        // GCS 로 변경
        VoiceRecordDto dto = googleSttService.generateGcsSignedUrlForSpeech(userId, speech.getId(), fileExtension);

        // Speech 에 objectName 저장
        speech.setFileUrl(dto.key());
        speechRepository.save(speech);

        return dto;
    }*/

}
