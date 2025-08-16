package com.example.speechmate_backend.oauth.dto;

import jakarta.validation.Valid;

public record AfterOauthSignupDto(
        String idToken,
        @Valid OnBoardingDto onBoardingDto
) {

}
