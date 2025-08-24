package com.example.speechmate_backend.speech.repository;

import com.example.speechmate_backend.speech.domain.Speech;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeechRepository extends JpaRepository<Speech, Long> {
}
