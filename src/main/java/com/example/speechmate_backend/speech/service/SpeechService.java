package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.common.aop.DistributedLock;
import com.example.speechmate_backend.common.exception.*;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.example.speechmate_backend.config.redis.RedisUtil;
import com.example.speechmate_backend.s3.MediaFileExtension;
import com.example.speechmate_backend.s3.controller.dto.VoiceKeyDto;
import com.example.speechmate_backend.s3.domain.PendingUpload;
import com.example.speechmate_backend.s3.repository.PendingUploadRepository;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.AnalysisStatus;
import com.example.speechmate_backend.speech.controller.SortType;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;


@Slf4j
@RequiredArgsConstructor
@Service
public class SpeechService {

    private final UserRepository userRepository;
    private final S3UploadPresignedUrlService s3UploadPresignedUrlService;
    private final SpeechRepository speechRepository;
    private final SpeechAnalysisResultService speechAnalysisResultService;
    private final SpeechCustomRepository speechCustomRepository;
    private final ReturnZeroClient returnZeroClient;
    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PendingUploadRepository pendingUploadRepository;

    /** speechId의 스피치를 userId 소유인 경우에만 돌려준다. 남의 speechId로 조회·분석·삭제하는 IDOR 차단. */
    private Speech findOwnedSpeech(Long speechId, Long userId) {
        Speech speech = speechRepository.findById(speechId)
                .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);
        if (!Objects.equals(speech.getUser().getId(), userId)) {
            throw UserNotMatchException.EXCEPTION;
        }
        return speech;
    }

    @Transactional
    public AnalysisResultDto analyze(Long speechId, Long userId) {
        Speech speech = findOwnedSpeech(speechId, userId);

        if (speech.getAnalysisResult() != null) {
            return AnalysisResultDto.from(speech.getAnalysisResult());
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
            //log.info("[AI 분석 성공] Speech ID {} 논리 점수: {}", speechId, result.getLogicalCoherenceScore());
            return AnalysisResultDto.from(result);
        } catch (SmateException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI 분석 실패] Speech ID {}", speechId, e);
            throw AiAnalysisException.EXCEPTION;
        }
    }

    /**
     * STT 접수 게이트. 완료됐으면 저장된 문장을, 진행 중이면 상태만, 아니면 작업을 접수하고 IN_PROGRESS를 돌려준다.
     * 실제 STT(S3 다운로드·ffmpeg·외부 API 폴링)는 커밋 뒤 SttJobRunner가 요청 스레드 밖에서 한다.
     * 분산 락은 "상태 확인 → IN_PROGRESS 전환"을 원자적으로 만들기 위해서만 쥔다. 예전엔 STT가 끝날 때까지(최대 5분) 쥐고 있었다.
     *
     * @param retry FAILED 상태를 다시 접수할지. 화면 진입 같은 "의도된 요청"은 true, 진행 중 폴링은 false로 보내
     *              실패한 작업이 폴링 주기마다 다시 돌지 않게 한다(S3 다운로드·외부 STT 호출이 매번 든다).
     */
    @DistributedLock(key = "'SPEECH_TRANSCRIBE:' + #speechId")
    public SttGateResponse rtzrStt(Long speechId, Long userId, boolean retry) {
        Speech speech = findOwnedSpeech(speechId, userId);

        VerbalAnalysisResult verbal = speech.getVerbalAnalysisResult();
        if (verbal != null && verbal.getSentencesJson() != null && !verbal.getSentencesJson().isEmpty()) {
            return SttGateResponse.completed(readSentences(verbal.getSentencesJson())); // 상태 컬럼 도입 전 데이터도 여기로
        }
        if (speech.getSttStatus() == AnalysisStatus.IN_PROGRESS
                || (speech.getSttStatus() == AnalysisStatus.FAILED && !retry)) {
            return SttGateResponse.statusOnly(speech.getSttStatus());
        }
        String fileKey = speech.getFileUrl();
        if (fileKey == null || fileKey.isEmpty()) {
            throw SpeechFileKeyNotFoundException.EXCEPTION;
        }

        String jobToken = UUID.randomUUID().toString();
        speech.setSttStatus(AnalysisStatus.IN_PROGRESS);
        speech.setSttJobToken(jobToken);
        speechRepository.save(speech);
        eventPublisher.publishEvent(new SttJobRunner.JobRequested(speechId, jobToken)); // 커밋 뒤 실행. 롤백되면 버려진다
        log.info("STT 작업 접수 (Speech ID: {})", speechId);
        return SttGateResponse.statusOnly(AnalysisStatus.IN_PROGRESS);
    }

    private List<SentenceDto> readSentences(String sentencesJson) {
        try {
            return objectMapper.readValue(sentencesJson, new TypeReference<List<SentenceDto>>() {});
        } catch (JsonProcessingException e) {
            log.error("저장된 sentencesJson 파싱 실패", e);
            throw ReturnZeroException.EXCEPTION;
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

    private static final long MAX_FILE_SIZE_BYTES = 500L * 1024 * 1024; // 500MB

    @Transactional
    public SpeechS3CallbackDto registerUploadedSpeech(Long userId, String fileKey, Long durationSeconds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.EXCEPTION);

        String ext = fileKey.contains(".")
                ? fileKey.substring(fileKey.lastIndexOf('.') + 1).toUpperCase()
                : "";
        try {
            MediaFileExtension.valueOf(ext);
        } catch (IllegalArgumentException e) {
            throw InvalidFileExtensionException.EXCEPTION;
        }

        PendingUpload pending = pendingUploadRepository
                .findByS3KeyAndUserId(fileKey, userId)
                .orElseThrow(() -> SpeechFileKeyNotFoundException.EXCEPTION);

        try {
            long fileSize = s3UploadPresignedUrlService.getObjectContentLength(fileKey);
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                s3UploadPresignedUrlService.deleteObject(fileKey);
                throw FileTooLargeException.EXCEPTION;
            }
        } catch (AmazonS3Exception e) {
            log.error("S3 파일 메타데이터 조회 실패 (key={}): {}", fileKey, e.getMessage());
            throw SpeechFileKeyNotFoundException.EXCEPTION;
        }

        Speech speech = new Speech();
        speech.setFileUrl(fileKey);

        String mediaType = ext.equals("MP4") || ext.equals("MOV") ? "VIDEO" : "AUDIO";
        speech.updateMediaInfo(durationSeconds, mediaType);

        user.addSpeech(speech);
        speechRepository.save(speech);

        pendingUploadRepository.delete(pending);

        String s3Url = s3UploadPresignedUrlService.getPublicS3Url(speech.getFileUrl());
        return SpeechS3CallbackDto.of(speech.getId(), s3Url);
    }

    /** limit+1개를 조회해 온 목록을 limit개로 자르고, 다음 페이지 유무와 커서를 계산한다. */
    private record CursorPage<T>(List<T> rows, boolean hasNext, CursorDto cursor) {}

    private static <T> CursorPage<T> slice(List<T> rows, int limit, Function<T, CursorDto> cursorOf) {
        if (rows.size() <= limit) {
            return new CursorPage<>(rows, false, null);
        }
        List<T> page = new ArrayList<>(rows.subList(0, limit));
        return new CursorPage<>(page, true, cursorOf.apply(page.get(limit - 1)));
    }

    private SpeechAnalysisResponseDto toAnalysisDto(Speech speech) {
        AnalysisResult ar = speech.getAnalysisResult();
        return new SpeechAnalysisResponseDto(
                speech.getId(),
                speech.getCreatedAt(),
                s3UploadPresignedUrlService.getPublicS3Url(speech.getFileUrl()),
                speech.getContent(),
                ar != null ? ar.getSummary() : null,
                ar != null ? ar.getKeywords() : null,
                ar != null ? ar.getImprovementPoints() : Collections.emptyList(),
                ar != null ? ar.getFeedback() : null,
                ar != null ? ar.getExpectedQuestions() : Collections.emptyList(),
                ar != null
        );
    }

    private SpeechPagingResponseDto toAnalysisPage(List<Speech> speeches, int limit) {
        CursorPage<Speech> page = slice(speeches, limit, sp -> new CursorDto(sp.getCreatedAt(), sp.getId()));
        return SpeechPagingResponseDto.builder()
                .speeches(page.rows().stream().map(this::toAnalysisDto).toList())
                .hasNext(page.hasNext())
                .cursordto(page.cursor())
                .build();
    }

    @Transactional(readOnly = true)
    public SpeechPagingResponseDto getAnalyzedSpeeches(Long userId, Long lastSpeechId, int limit) {
        Pageable pageable = PageRequest.of(0, limit + 1); // hasNext 판정용으로 1개 더
        return toAnalysisPage(speechRepository.findAnalyzedSpeeches(userId, lastSpeechId, pageable), limit);
    }

    @Transactional(readOnly = true)
    public SpeechPagingResponseDto getAllSpeeches(Long userId, Long lastSpeechId, int limit) {
        Pageable pageable = PageRequest.of(0, limit + 1);
        return toAnalysisPage(speechRepository.findAllSpeechesWithAnalysis(userId, lastSpeechId, pageable), limit);
    }

    @Transactional(readOnly = true)
    public SpeechPagingFeedDto getMySpeecheFeed(Long userId, Long lastSpeechId, int limit, SortType sortType) {
        List<SpeechFeedDto> rawDtos = speechCustomRepository.findMyFeed(userId, lastSpeechId, limit + 1, sortType);
        CursorPage<SpeechFeedDto> page = slice(rawDtos, limit, dto -> new CursorDto(dto.createdAt(), dto.id()));

        List<SpeechFeedDto> processedDtos = page.rows().stream()
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
                .hasNext(page.hasNext())
                .cursordto(page.cursor())
                .build();
    }

@Transactional
public SpeechIdDto addMetadataToSpeech(Long speechId, SpeechMetadataRequestDto requestDto, Long userId) {
    Speech speech = findOwnedSpeech(speechId, userId);

    speech.updateMetadata(
            requestDto.title(),
            requestDto.presentationContext(),
            requestDto.audience(),
            requestDto.location()
    );


    return SpeechIdDto.of(speechId);
}


public SpeechResultDto getSpeechById(Long speechId, Long userId) {
    Speech speech = findOwnedSpeech(speechId, userId);
    String s3Url = s3UploadPresignedUrlService.getPublicS3Url(speech.getFileUrl());

    SpeechResultDto dto = SpeechResultDto.fromE(speech, s3Url);
    return dto;

}

public SpeechContentResponse getSpeechContentById(Long speechId, Long userId) {
    Speech speech = findOwnedSpeech(speechId, userId);

    SpeechContentResponse dto = SpeechContentResponse.of(speech.getContent());
    return dto;
}

public SpeechConfigDto getSpeechConfigById(Long speechId, Long userId) {
    Speech speech = findOwnedSpeech(speechId, userId);
    String s3Url = s3UploadPresignedUrlService.getPublicS3Url(speech.getFileUrl());
    SpeechConfigDto dto = SpeechConfigDto.from(speech, s3Url);

    return dto;
}

public AnalysisResultDto getSpeechContentAnalysisById(Long speechId, Long userId) {
    Speech speech = findOwnedSpeech(speechId, userId);


    return AnalysisResultDto.from(speech.getAnalysisResult());

}


    @Transactional
    public void deleteSpeechById(Long speechId, Long userId) {
        Speech speech = findOwnedSpeech(speechId, userId);

        String fileKey = speech.getFileUrl();
        if (fileKey != null && !fileKey.isEmpty()) {
            s3UploadPresignedUrlService.deleteObject(fileKey);
            log.info("Deleting S3 object: {}", fileKey);
        }

        speechRepository.delete(speech);
    }


    public VerbalAnalysisDto getSpeechVerbalAnalysisById(Long speechId, Long userId) {
        Speech speech = findOwnedSpeech(speechId, userId);
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
    public NonVerbalAnalysisGateResponse requestNonVerbalAnalysis(Long speechId, Long userId) {
        Speech speech = findOwnedSpeech(speechId, userId);

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

        // (여기부터는 NOT_STARTED 또는 FAILED 상태만 넘어옴 — FAILED는 재요청 허용)

        // 3. Redis Stream에 보낼 메시지(Job) 생성
        // (Python이 speechId와 s3Key를 모두 알아야 함)
        String jobToken = UUID.randomUUID().toString();
        NonVerbalAnalysisRequest requestDto = new NonVerbalAnalysisRequest(speechId, s3Key, jobToken);

        try {
            // 4. Streams에 넣을 작업(Job). Python이 JSON으로 쉽게 파싱하도록 body 필드 하나에 직렬화
            String messageBody = objectMapper.writeValueAsString(requestDto);
            Map<String, String> jobData = Collections.singletonMap("job", messageBody);

            // XADD는 커밋 뒤, 요청 스레드 밖에서 한다 (NonVerbalJobPublisher). 롤백되면 이벤트가 버려져 유령 작업이 안 남고,
            // Redis가 느릴 때 이 요청이 DB 커넥션을 쥔 채 기다리지 않는다. 발행 실패는 publisher가 FAILED로 돌린다.
            eventPublisher.publishEvent(new NonVerbalJobPublisher.JobRequested(speechId, jobToken, jobData));

            // 5. [핵심] 상태를 'IN_PROGRESS' (분석 중)으로 변경
            speech.setNonVerbalStatus(AnalysisStatus.IN_PROGRESS);
            speech.setNonVerbalJobToken(jobToken); // 이전 시도의 늦은 콜백은 토큰 불일치로 무시됨
            speechRepository.save(speech); // 상태 변경 저장

            log.info("비언어적 분석 작업 발행 예약 (Speech ID: {}). 상태: IN_PROGRESS", speechId);

            return NonVerbalAnalysisGateResponse.statusOnly(AnalysisStatus.IN_PROGRESS);

        } catch (JsonProcessingException e) {
            log.error("Redis Stream 작업 직렬화 실패 (Speech ID: {})", speechId, e);
            throw NonVerbalAnalysisException.EXCEPTION;
        }
    }
}
