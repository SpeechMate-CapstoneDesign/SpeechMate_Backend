package com.example.speechmate_backend.speech.controller.dto;

import java.util.List;

public record TranscriptionResponse(
        String id,
        String status,
        Results results
) {
    public record Results(
            List<Utterance> utterances,
            boolean verified
    ) {}

    public record Utterance(
            int start_at,
            int duration,
            int spk,
            String spk_type,
            List<Word> words,
            String msg
    ) {}

    public record Word(
            int start_at,
            int duration,
            String text
    ) {}
}
