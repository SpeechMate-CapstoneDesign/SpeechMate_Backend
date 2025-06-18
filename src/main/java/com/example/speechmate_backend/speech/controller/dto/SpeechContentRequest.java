package com.example.speechmate_backend.speech.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record SpeechContentRequest(
        @NotBlank String content
        ) {
}
