package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.common.ApiResponse;
import com.example.speechmate_backend.common.exception.*;
import com.example.speechmate_backend.s3.MediaFileExtension;
import com.example.speechmate_backend.s3.controller.dto.VoiceKeyDto;
import com.example.speechmate_backend.s3.controller.dto.VoiceRecordDto;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.controller.SpeechRestClient;
import com.example.speechmate_backend.speech.controller.dto.*;
import com.example.speechmate_backend.speech.domain.AnalysisResult;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.SpeechCustomRepository;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.user.domain.User;
import com.example.speechmate_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Slf4j
@RequiredArgsConstructor
@Service
public class SpeechService {

    private final UserRepository userRepository;
    private final S3UploadPresignedUrlService s3UploadPresignedUrlService;
    private final SpeechRepository speechRepository;
    private final SpeechAnalysisResultService speechAnalysisResultService;
    private final SpeechRestClient speechRestClient;
    private final SpeechCustomRepository speechCustomRepository;

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


    public ResponseEntity<ApiResponse<String>> transcribeversion2(MultipartFile file, Long speechId) {
        try {
            Speech speech = speechRepository.findById(speechId)
                    .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);
            if(speech.getContent() != null && !speech.getContent().isEmpty()) {
                throw SpeechContentAlreadyExistException.EXCEPTION;
            }

            // FileSystemResource와 파일명 헤더 포함해서 보내기
            String content = speechRestClient.transcribeversion2(file);
            speech.setContent(content);
            speechRepository.save(speech);

            return ResponseEntity.ok(ApiResponse.ok(content));
        } catch (Exception e) {
            throw new RuntimeException("Whisper 변환 실패: " + e.getMessage(), e);
        }
    }

    public ResponseEntity<ApiResponse<String>> transcribeversionFromS3(String fileKey, Long speechId) {
        try {
            Speech speech = speechRepository.findById(speechId)
                    .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);
            if(speech.getContent() != null && !speech.getContent().isEmpty()) {
                throw SpeechContentAlreadyExistException.EXCEPTION;
            }
            if(!Objects.equals(speech.getFileUrl(), fileKey)) {
                throw SpeechFileKeyNotEqualException.EXCEPTION;
            }
            String content = speechRestClient.transcribeversionFromS3(fileKey);
            speech.setContent(content);
            speechRepository.save(speech);

            return ResponseEntity.ok(ApiResponse.ok(content));
        } catch (Exception e) {
            throw new RuntimeException("Whisper 변환 실패: " + e.getMessage(), e);
        }

    }




    @Transactional
    public VoiceKeyDto createPresignedUrlS3(Long userId, MediaFileExtension fileExtension) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.EXCEPTION);
        log.info("userID: " + userId);

        // 2. Presigned URL 발급
        VoiceKeyDto dto = s3UploadPresignedUrlService.generatePreSignedUrlForSpeech(userId, fileExtension);

        return dto;
    }

    @Transactional
    public SpeechIdDto registerUploadedSpeech(Long userId, String fileKey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.EXCEPTION);

        Speech speech = new Speech();
        speech.setFileUrl(fileKey); // 실제 s3 key
        user.addSpeech(speech);
        speechRepository.save(speech);

        return SpeechIdDto.of(speech.getId());
    }


    public SpeechPagingResponseDto getNextSpeeches(Long userId, Long lastSpeechId, int limit) {
        List<SpeechAnalysisResponseDto> speeches = speechCustomRepository.findNextSpeeches(userId, lastSpeechId, limit + 1) //hasNext를 알기 위해 +1을 해줍니다.
                .stream()
                .map(dto -> {
                    String publicUrl = s3UploadPresignedUrlService.getPublicS3Url(dto.fileUrl());
                    return new SpeechAnalysisResponseDto(
                            dto.speechId(),
                            dto.createdAt(),
                            publicUrl, // fileUrl만 교체
                            dto.content(),
                            dto.summary(),
                            dto.keywords(),
                            dto.improvementPoints(),
                            dto.logicalCoherenceScore(),
                            dto.feedback(),
                            dto.scoreExplanation(),
                            dto.expectedQuestions()
                    );
                })
                .collect(Collectors.toList());


        boolean hasNext = speeches.size() > limit;
        if (hasNext) {
            speeches = speeches.subList(0, limit);
        }

        CursorDto cursorDto = speeches.isEmpty() ?
                null : new CursorDto(speeches.get(speeches.size() - 1).createdAt(), speeches.get(speeches.size() - 1).speechId());

        return SpeechPagingResponseDto.builder()
                .speeches(speeches)
                .hasNext(hasNext)
                .cursordto(cursorDto)
                .build();
    }
}
