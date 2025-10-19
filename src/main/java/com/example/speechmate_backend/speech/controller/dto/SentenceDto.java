package com.example.speechmate_backend.speech.controller.dto;

public record SentenceDto(
        int startTime,
        String sentence
) {
    public static SentenceDto from(TranscriptionResponse.Utterance utterance) {
        return new SentenceDto(utterance.start_at(), utterance.msg());
    }
}
