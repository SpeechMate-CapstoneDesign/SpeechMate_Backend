package com.example.core.speech.repository;

import com.example.core.speech.domain.VerbalAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerbalAnalysisResultRepository extends JpaRepository<VerbalAnalysisResult, Long> {
}
