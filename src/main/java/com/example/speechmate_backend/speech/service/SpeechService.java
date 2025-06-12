package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.common.exception.SpeechNotFoundException;
import com.example.speechmate_backend.common.exception.UserNotFoundException;
import com.example.speechmate_backend.s3.MediaFileExtension;
import com.example.speechmate_backend.s3.controller.dto.VoiceRecordDto;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.controller.dto.SpeechContentRequest;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.user.domain.User;
import com.example.speechmate_backend.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class SpeechService {

    private final UserRepository userRepository;
    private final S3UploadPresignedUrlService s3UploadPresignedUrlService;
    private final SpeechRepository speechRepository;


    @Transactional
    public void getContent(Long speechId, @Valid SpeechContentRequest request) {
        Speech speech = speechRepository.findById(speechId)
                .orElseThrow(() -> SpeechNotFoundException.EXCEPTION);

        speech.setContent(request.content());
        speechRepository.save(speech);

        return ;

    }

    @Transactional
    public VoiceRecordDto createPresignedUrl(Long userId, MediaFileExtension fileExtension) {
        // 1. Speech 객체 저장 (content, audioFileUrl은 아직 null)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.EXCEPTION);

        Speech speech = new Speech();
        speech.setUser(user);
        speechRepository.save(speech); // 이 시점에 speechId 생성

        // 2. Presigned URL 발급
        VoiceRecordDto dto = s3UploadPresignedUrlService.generatePreSignedUrlForSpeech(userId, speech.getId(), fileExtension);

        //Speech에 S3 Key 저장
        speech.setFileUrl(dto.key());

        speechRepository.save(speech);

        return dto;
    }
}
