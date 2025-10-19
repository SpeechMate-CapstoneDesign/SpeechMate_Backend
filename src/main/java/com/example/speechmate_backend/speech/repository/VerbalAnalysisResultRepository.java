package com.example.speechmate_backend.speech.repository;

import com.example.speechmate_backend.speech.domain.VerbalAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerbalAnalysisResultRepository extends JpaRepository<VerbalAnalysisResult, Long> {
    Optional<VerbalAnalysisResult> findBySpeechId(Long id);
}
