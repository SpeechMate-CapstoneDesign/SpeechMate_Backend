package com.example.core.oauth.dto;

import jakarta.validation.Valid;

public record AfterOauthSignupDto(
        String idToken,
        @Valid OnBoardingDto onBoardingDto
) {

}
