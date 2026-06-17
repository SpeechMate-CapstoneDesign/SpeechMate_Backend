package com.example.speechmate_backend.speech.repository;

import com.example.speechmate_backend.speech.AnalysisStatus;
import com.example.speechmate_backend.speech.domain.Speech;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SpeechRepository extends JpaRepository<Speech, Long> {
    @Query("SELECT s FROM Speech s LEFT JOIN FETCH s.analysisResult ar " +
            "WHERE s.user.id = :userId AND (:lastId IS NULL OR s.id < :lastId) " +
            "ORDER BY s.id DESC")
    List<Speech> findAllSpeechesWithAnalysis(@Param("userId") Long userId, @Param("lastId") Long lastSpeechId, Pageable pageable);


    @Query("SELECT s FROM Speech s JOIN s.analysisResult ar WHERE s.user.id = :userId AND (:lastId IS NULL OR s.id < :lastId) ORDER BY s.id DESC")
    List<Speech> findAnalyzedSpeeches(@Param("userId") Long userId, @Param("lastId") Long lastSpeechId, Pageable pageable);

    List<Speech> findByNonVerbalStatusAndModifiedAtBefore(AnalysisStatus status, LocalDateTime cutoff);

}
