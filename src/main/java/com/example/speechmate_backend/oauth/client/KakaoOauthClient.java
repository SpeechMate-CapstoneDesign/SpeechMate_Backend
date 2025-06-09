package com.example.speechmate_backend.oauth.client;

import com.example.speechmate_backend.oauth.dto.OIDCPublickeyResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
        name = "KakaoOauthClient",
        url = "https://kauth.kakao.com"
)
public interface KakaoOauthClient {


    @GetMapping("/.well-known/jwks.json")
    OIDCPublickeyResponse getOIDCPublickeys();
}
