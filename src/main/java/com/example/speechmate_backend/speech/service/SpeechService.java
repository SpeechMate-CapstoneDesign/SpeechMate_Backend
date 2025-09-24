package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.common.ApiResponse;
import com.example.speechmate_backend.common.aop.DistributedLock;
import com.example.speechmate_backend.common.exception.*;
import com.example.speechmate_backend.s3.MediaFileExtension;
import com.example.speechmate_backend.s3.controller.dto.VoiceKeyDto;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.controller.SortType;
import com.example.speechmate_backend.speech.controller.SpeechRestClient;
import com.example.speechmate_backend.speech.controller.dto.*;
import com.example.speechmate_backend.speech.domain.AnalysisResult;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.domain.VerbalAnalysisResult;
import com.example.speechmate_backend.speech.repository.SpeechCustomRepository;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.speech.returnzero.ReturnZeroClient;
import com.example.speechmate_backend.user.domain.User;
import com.example.speechmate_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final ReturnZeroClient returnZeroClient;

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;

    @Transactional
    public AnalysisResultDto analyze(Long speechId) {
        Speech speech = speechRepository.findById(speechId)
                .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);

        if (speech.getAnalysisResult() != null) {
            AnalysisResult res = speech.getAnalysisResult();
            String fileUrl = s3UploadPresignedUrlService.getPublicS3Url(speech.getFileUrl());
            return AnalysisResultDto.from(res);
            //throw SpeechContentAlreadyExistException.EXCEPTION; // 이미 분석된 경우 종료
        }

        if (speech.getContent() == null || speech.getContent().isEmpty()) {
            throw SpeechContentNotExistException.EXCEPTION;
        }

        try {
            log.info("Speech ID {}에 대한 텍스트 분석을 시작합니다.", speechId);

            AnalysisResult result = speechAnalysisResultService.analyzeText(speech, speech.getContent());
            speech.setAnalysisResult(result);
            speechRepository.save(speech);
            String fileUrl = s3UploadPresignedUrlService.getPublicS3Url(speech.getFileUrl());
            //log.info("[AI 분석 성공] Speech ID {} 논리 점수: {}", speechId, result.getLogicalCoherenceScore());
            return AnalysisResultDto.from(result);
        } catch (Exception e) {
            log.error("[AI 분석 실패] Speech ID {}: {}", speechId, e.getMessage(), e);
            // 원하면 AI 실패 시 따로 정의된 예외로 던질 수도 있음
            throw new IllegalStateException("AI 분석 실패: " + e.getMessage(), e);
        }
    }

    public SpeechContentResponse rtzrStt(Long speechId) {
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

            String rtzrId = returnZeroClient.rtzrSttFromS3(fileKeyFromDb);
            TranscriptionResponse transcriptionResponse = returnZeroClient.rtzrTranscription(rtzrId);
            String content = speechAnalysisResultService.verbalanalyze(speech, transcriptionResponse);

            speechRepository.save(speech);

            return SpeechContentResponse.of(content);
        } catch (Exception e) {
            throw ReturnZeroException.EXCEPTION;
        }

    }

    //테스트용
    public void testrtzrStt(String rtzrId) {
        try {

            String transcriptionResponse = returnZeroClient.testrtzrTranscription(rtzrId);
            //speechAnalysisResultService.verbalanalyze(transcriptionResponse);
            System.out.println(transcriptionResponse);
        } catch (Exception e) {
            throw ReturnZeroException.EXCEPTION;
        }

    }

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

    @DistributedLock(key = "'SPEECH_TRANSCRIBE:' + #speechId")
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

            String content = speechRestClient.transcribeWithFileFromS3(fileKeyFromDb);
            speech.setContent(content);
            speechRepository.save(speech);

            return SpeechContentResponse.of(content);
        } catch (Exception e) {
            throw new RuntimeException("Whisper 변환 실패: " + e.getMessage(), e);
        }

    }

    public SpeechContentResponse transcribeversionFromS3WithoutLock(Long speechId) {
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

            String content = speechRestClient.transcribeWithFileFromS3(fileKeyFromDb);
            speech.setContent(content);
            speechRepository.save(speech);

            return SpeechContentResponse.of(content);
        } catch (Exception e) {
            throw new RuntimeException("Whisper 변환 실패: " + e.getMessage(), e);
        }

    }

    // mp4 -> mp3 테스트
    public String testtranscription(Long speechId) {
        try {
            Speech speech = speechRepository.findById(speechId)
                    .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);


            String fileKeyFromDb = speech.getFileUrl();
            if (fileKeyFromDb == null || fileKeyFromDb.isEmpty()) {
                // fileKey가 DB에 없는 경우에 대한 예외 처리
                throw SpeechFileKeyNotFoundException.EXCEPTION;
            }

            return speechRestClient.transcribeLargeFile(fileKeyFromDb);

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

    @CacheEvict(value = "speechFeedCache", allEntries = true)
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
        CursorDto cursorDto = null;

        if (hasNext) {
            Speech nextCursorSpeech = speeches.get(limit-1);
            cursorDto = new CursorDto(nextCursorSpeech.getCreatedAt(), nextCursorSpeech.getId());
            speeches.remove(limit);
        }


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


        return SpeechPagingResponseDto.builder()
                .speeches(dtoList)
                .hasNext(hasNext)
                .cursordto(cursorDto)
                .build();
    }


    @Transactional(readOnly = true)
    public SpeechPagingResponseDto getAllSpeeches(Long userId, Long lastSpeechId, int limit) {
        Pageable pageable = PageRequest.of(0, limit + 1); // hasNext 체크용으로 +1 조회

        List<Speech> speeches = speechRepository.findAllSpeechesWithAnalysis(userId, lastSpeechId, pageable);

        boolean hasNext = speeches.size() > limit;
        CursorDto cursorDto = null;

        if (hasNext) {
            Speech nextCursorSpeech = speeches.get(limit-1);
            cursorDto = new CursorDto(nextCursorSpeech.getCreatedAt(), nextCursorSpeech.getId());
            speeches.remove(limit);
        }


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

        return SpeechPagingResponseDto.builder()
                .speeches(dtoList)
                .hasNext(hasNext)
                .cursordto(cursorDto)
                .build();
    }


@Transactional(readOnly = true)
//@Cacheable(value = "speechFeedCache", key = "#userId + '_' + #lastSpeechId + '_' + #limit + '_' + #sortType")
public SpeechPagingFeedDto getMySpeecheFeed(Long userId, Long lastSpeechId, int limit, SortType sortType) {
    List<SpeechFeedDto> rawDtos = speechCustomRepository.findMyFeed(userId, lastSpeechId, limit + 1, sortType);

    boolean hasNext = rawDtos.size() > limit;
    CursorDto cursorDto = null;

    // 다음 페이지가 존재할 경우에만 커서 정보를 설정하고, 마지막 초과분 데이터 제거
    if (hasNext) {
        SpeechFeedDto nextCursorData = rawDtos.get(limit-1);
        cursorDto = new CursorDto(nextCursorData.createdAt(), nextCursorData.id());
        rawDtos.remove(limit);
    }

    List<SpeechFeedDto> processedDtos = rawDtos.stream()
            .map(dto -> new SpeechFeedDto(
                    dto.id(),
                    dto.title(),
                    dto.createdAt(),
                    dto.duration(),
                    dto.fileType(),
                    s3UploadPresignedUrlService.getPublicS3Url(dto.fileUrl()),
                    dto.presentationContext(),
                    dto.audience(),
                    dto.location()
            ))
            .toList();


    return SpeechPagingFeedDto.builder()
            .speeches(processedDtos)
            .hasNext(hasNext)
            .cursordto(cursorDto)
            .build();
}

@CacheEvict(value = "speechFeedCache", allEntries = true)
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


    @CacheEvict(value = "speechFeedCache", allEntries = true)
    public void deleteSpeechById(Long speechId, Long userId) {
        Speech speech = speechRepository.findById(speechId).orElseThrow(() -> SpeechNotFoundException.EXCEPTION);

        if(speech.getUser().getId() != userId) {
            throw UserNotMatchException.EXCEPTION;
        }

        String fileKey = speech.getFileUrl();
        if (fileKey != null && !fileKey.isEmpty()) {
            s3UploadPresignedUrlService.deleteObject(fileKey);
            log.info("Deleting S3 object: {}", fileKey);
        }

        speechRepository.delete(speech);
    }


    public VerbalAnalysisDto getSpeechVerbalAnalysisById(Long speechId) {

        Speech speech = speechRepository.findById(speechId).orElseThrow(() -> SpeechNotFoundException.EXCEPTION);
        VerbalAnalysisResult verbalAnalysisResult = speech.getVerbalAnalysisResult();

        return VerbalAnalysisDto.from(verbalAnalysisResult);
    }
}
