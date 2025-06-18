package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.common.exception.SpeechNotFoundException;
import com.example.speechmate_backend.common.exception.UserNotFoundException;
import com.example.speechmate_backend.s3.MediaFileExtension;
import com.example.speechmate_backend.s3.controller.dto.VoiceRecordDto;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.controller.dto.AnalysisResultDto;
import com.example.speechmate_backend.speech.controller.dto.SpeechContentRequest;
import com.example.speechmate_backend.speech.domain.AnalysisResult;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.user.domain.User;
import com.example.speechmate_backend.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class SpeechService {

    private final UserRepository userRepository;
    private final S3UploadPresignedUrlService s3UploadPresignedUrlService;
    private final SpeechRepository speechRepository;
    private final SpeechAnalysisResultService speechAnalysisResultService;


    @Transactional
    public void getContent(Long speechId, @Valid SpeechContentRequest request) {
        Speech speech = speechRepository.findById(speechId)
                .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);
        String originalContent = request.content();
        log.info("Speech ID {}에 대한 텍스트 분석을 시작합니다.", speechId);
        AnalysisResult analysisResult = speechAnalysisResultService.analyzeText(originalContent);
        log.info("텍스트 분석 완료. 논리 점수: {}", analysisResult.getLogicalCoherenceScore());
        speech.setAnalysisResult(analysisResult);
        speech.setContent(request.content());
        speechRepository.save(speech);


    }

    @Transactional
    public VoiceRecordDto createPresignedUrl(Long userId, MediaFileExtension fileExtension) {
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
}
