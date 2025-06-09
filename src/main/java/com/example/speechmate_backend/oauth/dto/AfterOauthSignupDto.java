package com.example.speechmate_backend.oauth.dto;

public record AfterOauthSignupDto(
        String idToken,
        OnBoardingDto onBoardingDto
) {

}
