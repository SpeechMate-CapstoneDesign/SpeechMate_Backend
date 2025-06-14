package com.example.speechmate_backend.user.controller;

import com.example.speechmate_backend.common.ApiResponse;
import com.example.speechmate_backend.common.exception.InvalidOauthProviderException;
import com.example.speechmate_backend.user.domain.OauthInfo;
import com.example.speechmate_backend.oauth.KakaoProperties;
import com.example.speechmate_backend.oauth.dto.AfterOauthSignupDto;
import com.example.speechmate_backend.oauth.dto.OauthLoginRequest;
import com.example.speechmate_backend.oauth.dto.OauthLoginResponse;
import com.example.speechmate_backend.oauth.helper.KakaoOauthHelper;
import com.example.speechmate_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RequiredArgsConstructor
@RequestMapping("/api/auth")
@RestController
public class AuthController {

    private final KakaoOauthHelper kakaoOauthHelper;
    private final UserService userService;
    private final KakaoProperties kakaoProperties;


    @PostMapping("/oauth/kakao/login")
    public ResponseEntity<ApiResponse<OauthLoginResponse>> loginKakao(@RequestBody OauthLoginRequest request) {
        if(!request.getProvider().equals("KAKAO")) {
            throw InvalidOauthProviderException.EXCEPTION;
        }
        OauthInfo oauthInfo = kakaoOauthHelper.getOauthInfoByKakaoIdToken(request.getIdToken());
        return ResponseEntity.ok(ApiResponse.ok(userService.loginUser(oauthInfo)));
    }


    @PostMapping("/oauth/kakao/signup")
    public ResponseEntity<ApiResponse<OauthLoginResponse>> signupWhenFirstOauthLogin(@RequestBody AfterOauthSignupDto afterOauthSignupDto) {
        return ResponseEntity.ok(ApiResponse.ok(userService.signupKakaoWhenFirstOauthLogin(afterOauthSignupDto)));
    }


/*
* idtoken 발급 테스트용
* */
    @GetMapping("/test")
    public ResponseEntity<String> receiveCode(@RequestParam String code) {
        return ResponseEntity.ok("코드 잘 받음: " + code);
    }

    @PostMapping("/issue-id-token")
    public ResponseEntity<String> issueIdToken(@RequestParam String code) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoProperties.getKakaoAppKey());
        params.add("redirect_uri", kakaoProperties.getKakaoRedirectUrI());
        params.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://kauth.kakao.com/oauth/token", request, Map.class);

        String idToken = (String) response.getBody().get("id_token");
        return ResponseEntity.ok(idToken);
    }


}
