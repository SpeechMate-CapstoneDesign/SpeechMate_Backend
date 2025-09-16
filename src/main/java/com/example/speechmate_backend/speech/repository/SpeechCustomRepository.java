package com.example.speechmate_backend.speech.repository;

import com.example.speechmate_backend.speech.controller.SortType;
import com.example.speechmate_backend.speech.controller.dto.SpeechFeedDto;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpeechCustomRepository {
    List<SpeechFeedDto> findMyFeed(Long userId, Long lastSpeechId, int limit, SortType sortType);
}
