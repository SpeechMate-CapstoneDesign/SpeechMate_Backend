package com.example.speechmate_backend.speech.controller.dto;

import java.time.LocalDateTime;

public record CursorDto(
        LocalDateTime dateTime,
        Long id
) {}

