package com.example.speechmate_backend.speech.repository;

import com.example.speechmate_backend.speech.domain.NonVerbalAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NonVerbalAnalysisResultRepository extends JpaRepository<NonVerbalAnalysisResult, Long> {
}
