package com.example.speechmate_backend.speech.repository;

import com.example.speechmate_backend.speech.domain.VerbalAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerbalAnalysisResultRepository extends JpaRepository<VerbalAnalysisResult, Long> {
}
