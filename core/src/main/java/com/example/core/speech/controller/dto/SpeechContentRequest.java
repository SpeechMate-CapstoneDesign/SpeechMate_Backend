package com.example.core.speech.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record SpeechContentRequest(
        @NotBlank String content
        ) {
}
