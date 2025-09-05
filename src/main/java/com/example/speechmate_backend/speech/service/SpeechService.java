package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.common.ApiResponse;
import com.example.speechmate_backend.common.exception.*;
import com.example.speechmate_backend.s3.MediaFileExtension;
import com.example.speechmate_backend.s3.controller.dto.VoiceKeyDto;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.controller.SortType;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
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
            String fileUrl = s3UploadPresignedUrlService.getPublicS3Url(speech.getFileUrl());
            return SpeechResultDto.from(speech, fileUrl);
            //throw SpeechContentAlreadyExistException.EXCEPTION; // 이미 분석된 경우 종료
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
            //log.info("[AI 분석 성공] Speech ID {} 논리 점수: {}", speechId, result.getLogicalCoherenceScore());
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
        if (speech.getContent() != null && !speech.getContent().isEmpty()) {
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
            if (speech.getContent() != null && !speech.getContent().isEmpty()) {
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

    public SpeechContentResponse transcribeversionFromS3(Long speechId) {
        try {
            Speech speech = speechRepository.findById(speechId)
                    .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);
            if (speech.getContent() != null && !speech.getContent().isEmpty()) {
                return SpeechContentResponse.of(speech.getContent());
            }

            String fileKeyFromDb = speech.getFileUrl();
            if (fileKeyFromDb == null || fileKeyFromDb.isEmpty()) {
                // fileKey가 DB에 없는 경우에 대한 예외 처리
                throw SpeechFileKeyNotFoundException.EXCEPTION;
            }

            String content = speechRestClient.transcribeversionFromS3(fileKeyFromDb);
            speech.setContent(content);
            speechRepository.save(speech);

            return SpeechContentResponse.of(content);
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
    public SpeechS3CallbackDto registerUploadedSpeech(Long userId, String fileKey, Long durationSeconds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.EXCEPTION);

        Speech speech = new Speech();
        speech.setFileUrl(fileKey); // 실제 s3 key

        String mediaType = fileKey.toLowerCase().endsWith(".mp4") || fileKey.toLowerCase().endsWith(".mov") ? "VIDEO" : "AUDIO";
        speech.updateMediaInfo(durationSeconds, mediaType);

        user.addSpeech(speech);
        speechRepository.save(speech);
        String s3Url = s3UploadPresignedUrlService.getPublicS3Url(speech.getFileUrl());
        return SpeechS3CallbackDto.of(speech.getId(), s3Url);
    }

    @Transactional(readOnly = true)
    public SpeechPagingResponseDto getAnalyzedSpeeches(Long userId, Long lastSpeechId, int limit) {
        Pageable pageable = PageRequest.of(0, limit + 1); // hasNext 체크용

        List<Speech> speeches = speechRepository.findAnalyzedSpeeches(userId, lastSpeechId, pageable);

        boolean hasNext = speeches.size() > limit;
        if (hasNext) speeches = speeches.subList(0, limit);

        List<SpeechAnalysisResponseDto> dtoList = speeches.stream()
                .map(speech -> {
                    var ar = speech.getAnalysisResult();
                    String s3Url = s3UploadPresignedUrlService.getPublicS3Url(speech.getFileUrl());
                    return new SpeechAnalysisResponseDto(
                            speech.getId(),
                            speech.getCreatedAt(),
                            s3Url,
                            speech.getContent(),
                            ar.getSummary(),
                            ar.getKeywords(),
                            ar.getImprovementPoints(),
                            ar.getFeedback(),
                            ar.getExpectedQuestions(),
                            true
                    );
                })
                .collect(Collectors.toList());

        CursorDto cursorDto = dtoList.isEmpty() ? null :
                new CursorDto(
                        dtoList.get(dtoList.size() - 1).createdAt(),
                        dtoList.get(dtoList.size() - 1).speechId()
                );

        return SpeechPagingResponseDto.builder()
                .speeches(dtoList)
                .hasNext(hasNext)
                .cursordto(cursorDto)
                .build();
    }


    @Transactional(readOnly = true)
    public SpeechPagingResponseDto getAllSpeeches(Long userId, Long lastSpeechId, int limit) {
        Pageable pageable = PageRequest.of(0, limit + 1); // hasNext 체크용으로 +1 조회

        List<Speech> speeches = speechRepository.findAllSpeechesWithAnalysis(userId, pageable);

        // 커서 기반 페이징
        if (lastSpeechId != null) {
            speeches = speeches.stream()
                    .filter(s -> s.getId() < lastSpeechId)
                    .collect(Collectors.toList());
        }

        boolean hasNext = speeches.size() > limit;
        if (hasNext) speeches = speeches.subList(0, limit);

        List<SpeechAnalysisResponseDto> dtoList = speeches.stream()
                .map(speech -> {
                    var ar = speech.getAnalysisResult();
                    String s3Url = s3UploadPresignedUrlService.getPublicS3Url(speech.getFileUrl());
                    return new SpeechAnalysisResponseDto(
                            speech.getId(),
                            speech.getCreatedAt(),
                            s3Url,
                            speech.getContent(),
                            ar != null ? ar.getSummary() : null,
                            ar != null ? ar.getKeywords() : null,
                            ar != null ? ar.getImprovementPoints() : Collections.emptyList(),
                            ar != null ? ar.getFeedback() : null,
                            ar != null ? ar.getExpectedQuestions() : Collections.emptyList(),
                            ar != null
                    );
                })
                .collect(Collectors.toList());

        // 마지막 스피치 id를 커서로 사용
        CursorDto cursorDto = dtoList.isEmpty()
                ? null
                : new CursorDto(dtoList.get(dtoList.size() - 1).createdAt(),
                dtoList.get(dtoList.size() - 1).speechId()
        );

        return SpeechPagingResponseDto.builder()
                .speeches(dtoList)
                .hasNext(hasNext)
                .cursordto(cursorDto)
                .build();
    }


private SpeechPagingResponseDto buildPagingResponse(List<SpeechAnalysisResponseDto> speeches, int limit) {
    // S3 public URL로 변환
    List<SpeechAnalysisResponseDto> content = speeches.stream()
            .map(dto -> new SpeechAnalysisResponseDto(
                    dto.speechId(),
                    dto.createdAt(),
                    s3UploadPresignedUrlService.getPublicS3Url(dto.fileUrl()), // URL 변환
                    dto.content(),
                    dto.summary(),
                    dto.keywords(),
                    dto.improvementPoints(),
                    dto.feedback(),
                    dto.expectedQuestions(),
                    dto.isAnalyzed() // isAnalyzed 값 전달
            ))
            .collect(Collectors.toList());

    boolean hasNext = content.size() > limit;
    if (hasNext) {
        content.remove(limit); // 마지막 항목 제거
    }

    CursorDto cursorDto = content.isEmpty() ?
            null : new CursorDto(content.get(content.size() - 1).createdAt(), content.get(content.size() - 1).speechId());

    return SpeechPagingResponseDto.builder()
            .speeches(content)
            .hasNext(hasNext)
            .cursordto(cursorDto)
            .build();
}

@Transactional(readOnly = true)
public SpeechPagingFeedDto getMySpeecheFeed(Long userId, Long lastSpeechId, int limit, SortType sortType) {
    List<SpeechFeedDto> rawDtos = speechCustomRepository.findMyFeed(userId, lastSpeechId, limit + 1, sortType);

    List<SpeechFeedDto> processedDtos = rawDtos.stream()
            .map(dto -> new SpeechFeedDto(
                    dto.id(),
                    dto.title(),
                    dto.createdAt(),
                    dto.fileType(),
                    s3UploadPresignedUrlService.getPublicS3Url(dto.fileUrl()),
                    dto.presentationContext(),
                    dto.audience(),
                    dto.location()
            ))
            .toList();

    boolean hasNext = rawDtos.size() > limit;
    if (hasNext) {
        rawDtos.remove(limit);
    }

    CursorDto cursorDto = rawDtos.isEmpty() ? null : new CursorDto(rawDtos.get(rawDtos.size() - 1).createdAt(), rawDtos.get(rawDtos.size() - 1).id());

    // SpeechFeedDto의 createdAt 필드는 String이므로,
    // 정렬 기준이 createdAt인 경우 CursorDto의 필드도 이에 맞춰 수정.
    // 여기서는 ID만을 커서로 사용하는 간단한 방식 가정

    return SpeechPagingFeedDto.builder()
            .speeches(processedDtos)
            .hasNext(hasNext)
            .cursordto(cursorDto)
            .build();
}


@Transactional
public SpeechIdDto addMetadataToSpeech(Long speechId, SpeechMetadataRequestDto requestDto, Long userId) {
    Speech speech = speechRepository.findById(speechId)
            .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);

    if (!speech.getUser().getId().equals(userId)) {
        throw UserNotMatchException.EXCEPTION;
    }

    speech.updateMetadata(
            requestDto.title(),
            requestDto.presentationContext(),
            requestDto.audience(),
            requestDto.location()
    );


    return SpeechIdDto.of(speechId);
}


public SpeechResultDto getSpeechById(Long speechId) {
    Speech speech = speechRepository.findById(speechId).orElseThrow(() -> SpeechNotFoundException.EXCEPTION);
    String s3Url = s3UploadPresignedUrlService.getPublicS3Url(speech.getFileUrl());

    SpeechResultDto dto = SpeechResultDto.fromE(speech, s3Url);
    return dto;

}

public SpeechContentResponse getSpeechContentById(Long speechId) {
    Speech speech = speechRepository.findById(speechId).orElseThrow(() -> SpeechNotFoundException.EXCEPTION);

    SpeechContentResponse dto = SpeechContentResponse.of(speech.getContent());
    return dto;
}

public SpeechConfigDto getSpeechConfigById(Long speechId) {
    Speech speech = speechRepository.findById(speechId).orElseThrow(() -> SpeechNotFoundException.EXCEPTION);
    String s3Url = s3UploadPresignedUrlService.getPublicS3Url(speech.getFileUrl());
    SpeechConfigDto dto = SpeechConfigDto.from(speech, s3Url);

    return dto;
}

public AnalysisResultDto getSpeechContentAnalysisById(Long speechId) {
    Speech speech = speechRepository.findById(speechId).orElseThrow(() -> SpeechNotFoundException.EXCEPTION);


    return AnalysisResultDto.from(speech.getAnalysisResult());

}
}
