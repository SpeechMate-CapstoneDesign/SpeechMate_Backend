package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.common.ApiResponse;
import com.example.speechmate_backend.common.aop.DistributedLock;
import com.example.speechmate_backend.common.exception.*;
import com.example.speechmate_backend.config.redis.RedisUtil;
import com.example.speechmate_backend.s3.MediaFileExtension;
import com.example.speechmate_backend.s3.controller.dto.VoiceKeyDto;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.AnalysisStatus;
import com.example.speechmate_backend.speech.controller.SortType;
import com.example.speechmate_backend.speech.controller.SpeechRestClient;
import com.example.speechmate_backend.speech.controller.dto.*;
import com.example.speechmate_backend.speech.domain.AnalysisResult;
import com.example.speechmate_backend.speech.domain.NonVerbalAnalysisResult;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.domain.VerbalAnalysisResult;
import com.example.speechmate_backend.speech.repository.SpeechCustomRepository;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.speech.returnzero.ReturnZeroClient;
import com.example.speechmate_backend.user.domain.User;
import com.example.speechmate_backend.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    // Redis Stream에 사용할 작업 큐 이름 (상수)
    private static final String STREAM_KEY = "nonverbal-analysis-jobs";
    private static final int MAX_STREAM_LENGTH = 10000;

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

    @DistributedLock(key = "'SPEECH_TRANSCRIBE:' + #speechId")
    public SpeechSentenceResponse rtzrStt(Long speechId) {
        try {
            Speech speech = speechRepository.findById(speechId)
                    .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);

            TranscriptionResponse transcriptionResponse;
            String rawJson = speech.getRawTranscription();

            // ✅ 1. 'isNewAnalysis' -> 'needsNewAnalysis'로 이름 변경 (더 명확하게)
            boolean needsNewAnalysis = false;

            if (rawJson != null && !rawJson.isEmpty()) {
                // [캐시 있음]
                log.info("DB에서 STT JSON 원본을 로드합니다. (Speech ID: {})", speechId);
                transcriptionResponse = objectMapper.readValue(rawJson, TranscriptionResponse.class);

                // 캐시는 있지만, 분석 결과가 없으면 새로 분석 필요
                if (speech.getVerbalAnalysisResult() == null) {
                    log.warn("Speech ID {}: STT 캐시는 있으나 분석 결과가 없어, 재분석합니다.", speech.getId());
                    needsNewAnalysis = true;
                }
            } else {
                // [캐시 없음]
                log.info("vito.ai API를 호출하여 STT를 진행합니다. (Speech ID: {})", speechId);

                String fileKeyFromDb = speech.getFileUrl();
                if (fileKeyFromDb == null || fileKeyFromDb.isEmpty()) {
                    throw SpeechFileKeyNotFoundException.EXCEPTION;
                }

                String rtzrId = returnZeroClient.rtzrSttFromS3(fileKeyFromDb);
                transcriptionResponse = returnZeroClient.rtzrTranscription(rtzrId);

                String jsonResponse = objectMapper.writeValueAsString(transcriptionResponse);
                speech.setRawTranscription(jsonResponse);
                needsNewAnalysis = true; // 새로 API 호출했으니 무조건 분석 필요
            }

            // ✅ 2. '새로 분석이 필요할 때만' verbalanalyze 호출 및 저장
            if (needsNewAnalysis) {
                log.info("Speech ID {}: 음성 분석(verbalanalyze)을 실행합니다.", speech.getId());
                // 2-1. 분석 실행 (내부에서 VerbalAnalysisResult 생성 및 speech에 연결)
                speechAnalysisResultService.verbalanalyze(speech, transcriptionResponse);

                // 2-2. 'speech' 저장 (rawTranscription, 신규 VerbalAnalysisResult가 Cascade로 저장됨)
                speechRepository.save(speech);
                log.info("Speech 엔티티에 rawTranscription 및 새 분석 결과를 저장했습니다.");
            }

            // ✅ 3. 불필요한 fileKeyFromDb 중복 체크 제거
            // String fileKeyFromDb = speech.getFileUrl(); ... (이 부분 삭제)

            // 4. 프론트엔드 반환용 DTO 생성
            List<SentenceDto> sentences;
            if (transcriptionResponse.results() == null || transcriptionResponse.results().utterances() == null) {
                sentences = Collections.emptyList();
            } else {
                sentences = transcriptionResponse.results().utterances().stream()
                        .map(SentenceDto::from)
                        .collect(Collectors.toList());
            }

            // 5. 'sentencesJson' 저장 (이미 저장되어 있다면 중복 저장 방지)
            VerbalAnalysisResult verbalResult = speech.getVerbalAnalysisResult();

            if (verbalResult == null) {
                // 이 로직이 실행되면 심각한 오류 (needsNewAnalysis=true일 때 생성되었어야 함)
                log.error("Speech ID {}: 'sentencesJson' 저장 시점에 VerbalAnalysisResult가 null입니다.", speech.getId());
                throw new IllegalStateException("VerbalAnalysisResult가 존재하지 않습니다.");
            }

            // ✅ 6. 'sentencesJson' 필드가 비어있을 때만 새로 저장 (불필요한 DB UPDATE 방지)
            if (verbalResult.getSentencesJson() == null || verbalResult.getSentencesJson().isEmpty()) {
                try {
                    String sentencesJson = objectMapper.writeValueAsString(sentences);
                    verbalResult.setSentencesJson(sentencesJson);

                    // speech를 저장하여 verbalResult의 변경사항(sentencesJson)을 UPDATE
                    speechRepository.save(speech);
                    log.info("VerbalAnalysisResult에 sentencesJson을 저장했습니다.");

                } catch (JsonProcessingException e) {
                    log.error("sentences DTO 'List<SentenceDto>' JSON 직렬화 오류", e);
                    // 이 저장이 실패해도, 사용자에게 응답은 정상적으로 반환합니다.
                }
            }

            // 7. 최종 응답 반환
            return SpeechSentenceResponse.of(sentences);

        } catch (JsonProcessingException e) {
            log.error("STT JSON 직렬화/역직렬화 오류 발생", e);
            throw new RuntimeException("JSON 처리 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            log.error("rtzrStt 처리 중 예외 발생", e);
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

        redisUtil.uploadlimit(String.valueOf(userId));
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

    @Transactional(readOnly = true)
    public NonVerbalAnalysisResponse getSpeechNonVerbalAnalysisDto(NonVerbalAnalysisResult result) {
        if (result == null) return null;
        // DTO 변환 로직은 SpeechCallbackService에서 사용했던 objectMapper를 사용
        return NonVerbalAnalysisResponse.from(result, objectMapper);
    }

    /**
     * Python 서버에 비언어적 분석을 "요청"합니다. (비동기)
     * Redis Pub/Sub 대신 Streams (XADD)를 사용.
     * 완료된 작업은 재요청을 거부
     *
     * @param speechId 분석할 Speech의 ID
     * @return 202 (Accepted) 또는 409 (Conflict)
     */
    @Transactional // (상태 조회 및 변경을 위해 트랜잭션 사용)
    public NonVerbalAnalysisGateResponse requestNonVerbalAnalysis(Long speechId) {

        Speech speech = speechRepository.findById(speechId)
                .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);

        String s3Key = speech.getFileUrl();
        if (s3Key == null || s3Key.isEmpty()) {
            throw SpeechFileKeyNotFoundException.EXCEPTION; // (FileKey 예외가 있다고 가정)
        }

        AnalysisStatus currentStatus = speech.getNonVerbalStatus();

        // [수정] 1. 완료된 건 재요청 불가 -> 분석 결과를 불러오는걸로
        if (currentStatus == AnalysisStatus.COMPLETED) {
            log.warn("비언어적 분석 재요청 거부 (Speech ID: {}). 이미 'COMPLETED' 상태입니다.", speechId);
            NonVerbalAnalysisResponse dto = getSpeechNonVerbalAnalysisDto(speech.getNonVerbalAnalysisResult());
            return NonVerbalAnalysisGateResponse.builder()
                    .analysisStatus(currentStatus)
                    .result(dto)
                    .build();
        }

        // [수정] 2. 분석 중인 건 중복 요청 불가
        if (currentStatus == AnalysisStatus.IN_PROGRESS) {
            log.warn("비언어적 분석 중복 요청 거부 (Speech ID: {}). 현재 'IN_PROGRESS' 상태입니다.", speechId);
            return NonVerbalAnalysisGateResponse.statusOnly(currentStatus);
        }

        if (currentStatus == AnalysisStatus.FAILED) {
            log.warn("비언어적 분석 실패 (Speech ID: {}). 현재 'FAILED' 상태입니다.", speechId);
            return NonVerbalAnalysisGateResponse.statusOnly(currentStatus);
        }

        // (여기부터는 NOT_STARTED 또는 FAILED 상태만 넘어옴)

        // 3. Redis Stream에 보낼 메시지(Job) 생성
        // (Python이 speechId와 s3Key를 모두 알아야 함)
        NonVerbalAnalysisRequest requestDto = new NonVerbalAnalysisRequest(speechId, s3Key);

        try {
            // 4. [핵심 수정] Streams에 작업(Job)을 추가 (XADD)
            // Python이 작업을 JSON으로 쉽게 파싱할 수 있도록 body 필드 하나에 직렬화
            String messageBody = objectMapper.writeValueAsString(requestDto);
            Map<String, String> jobData = Collections.singletonMap("job", messageBody);

            // Stream에 메시지 추가
            redisTemplate.opsForStream().add(STREAM_KEY, jobData);

            // (참고: MapRecord.create(STREAM_KEY, jobData)를 사용하는 방법도 있습니다)

            // 5. [핵심] 상태를 'IN_PROGRESS' (분석 중)으로 변경
            speech.setNonVerbalStatus(AnalysisStatus.IN_PROGRESS);
            speechRepository.save(speech); // 상태 변경 저장

            log.info("비언어적 분석 작업 Stream 발행 (Speech ID: {}). 상태: IN_PROGRESS", speechId);

            return NonVerbalAnalysisGateResponse.statusOnly(AnalysisStatus.IN_PROGRESS);

        } catch (JsonProcessingException e) {
            log.error("Redis Stream 작업 직렬화 실패 (Speech ID: {}): {}", speechId, e.getMessage(), e);
            throw new RuntimeException("작업 생성 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            log.error("Redis Stream 발행 실패 (Speech ID: {}): {}", speechId, e.getMessage(), e);
            throw new RuntimeException("작업 발행 중 오류가 발생했습니다.", e);
        }
    }
}
