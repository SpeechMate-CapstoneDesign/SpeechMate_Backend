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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "인증", description = "소셜 로그인 및 회원가입 API")
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@RestController
public class AuthController {

    private final KakaoOauthHelper kakaoOauthHelper;
    private final UserService userService;
    private final KakaoProperties kakaoProperties;


    @Operation(summary = "카카오 로그인", description = "프론트에서 받은 Kakao ID 토큰을 통해 로그인합니다.")
    @PostMapping("/oauth/kakao/login")
    public ResponseEntity<ApiResponse<OauthLoginResponse>> loginKakao(@RequestBody OauthLoginRequest request) {
        if(!request.getProvider().equals("KAKAO")) {
            throw InvalidOauthProviderException.EXCEPTION;
        }
        OauthInfo oauthInfo = kakaoOauthHelper.getOauthInfoByKakaoIdToken(request.getIdToken());
        return ResponseEntity.ok(ApiResponse.ok(userService.loginUser(oauthInfo)));
    }

    @Operation(summary = "카카오 회원가입", description = "최초 카카오 로그인 사용자의 추가 정보와 함께 회원가입을 진행합니다.")
    @PostMapping("/oauth/kakao/signup")
    public ResponseEntity<ApiResponse<OauthLoginResponse>> signupWhenFirstOauthLogin(@RequestBody AfterOauthSignupDto afterOauthSignupDto) {
        return ResponseEntity.ok(ApiResponse.ok(userService.signupKakaoWhenFirstOauthLogin(afterOauthSignupDto)));
    }


/*
* idtoken 발급 테스트용
* */
@Operation(summary = "테스트용 코드 수신", description = "백엔드에서 카카오 redirect_uri에 붙는 code 값을 수신합니다. (테스트용)")
@GetMapping("/test")
    public ResponseEntity<String> receiveCode(@Parameter(description = "카카오 인증 후 redirect_uri에 포함된 code") @RequestParam String code) {
        return ResponseEntity.ok("코드 잘 받음: " + code);
    }

    @Operation(summary = "ID Token 발급", description = "백엔드 테스트용 - Kakao authorization code를 통해 ID Token을 발급합니다.")
    @PostMapping("/issue-id-token")
    public ResponseEntity<String> issueIdToken(@Parameter(description = "카카오 인가 코드") @RequestParam String code) {
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
