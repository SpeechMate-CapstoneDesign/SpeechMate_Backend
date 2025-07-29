package com.example.speechmate_backend.speech.repository;

import com.example.speechmate_backend.speech.controller.dto.SpeechAnalysisResponseDto;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpeechCustomRepository {
    List<SpeechAnalysisResponseDto> findNextSpeeches(Long userId, Long lastSpeechId, int limit);
}
