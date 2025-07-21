package com.example.speechmate_backend.user.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenReissueRequest(
        @NotBlank(message = "refresh token is required")
        String refreshToken
) {
}
