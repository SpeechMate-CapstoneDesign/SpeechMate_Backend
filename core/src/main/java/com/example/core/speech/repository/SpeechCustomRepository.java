package com.example.core.speech.repository;

import com.example.core.speech.controller.SortType;
import com.example.core.speech.controller.dto.SpeechFeedDto;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpeechCustomRepository {
    List<SpeechFeedDto> findMyFeed(Long userId, Long lastSpeechId, int limit, SortType sortType);
}
