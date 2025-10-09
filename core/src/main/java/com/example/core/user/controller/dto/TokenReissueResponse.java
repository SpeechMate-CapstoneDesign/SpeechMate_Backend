package com.example.core.user.controller.dto;

public record TokenReissueResponse(
        String access,
        String accessExpiredAt,
        String refresh,
        String refreshExpiredAt
) {
}
