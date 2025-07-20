package com.example.speechmate_backend.user.controller.dto;

public record TokenReissueResponse(
        String access,
        String accessExpiredAt,
        String refresh,
        String refreshExpiredAt
) {
}
