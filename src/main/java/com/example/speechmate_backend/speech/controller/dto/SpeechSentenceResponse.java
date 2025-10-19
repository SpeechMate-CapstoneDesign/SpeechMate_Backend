package com.example.speechmate_backend.speech.controller.dto;

import java.util.List;

public record SpeechSentenceResponse(
        List<SentenceDto> sentences
) {
    public static SpeechSentenceResponse of(List<SentenceDto> sentences) {
        return new SpeechSentenceResponse(sentences);
    }
}
