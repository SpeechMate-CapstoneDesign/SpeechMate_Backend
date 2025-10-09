package com.example.core.speech.controller.dto;

import java.time.LocalDateTime;

public record CursorDto(
        LocalDateTime dateTime,
        Long id
) {}

