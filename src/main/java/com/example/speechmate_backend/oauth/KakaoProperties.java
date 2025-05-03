package com.example.speechmate_backend.oauth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties("oauth")
public class KakaoProperties {

    private final OauthSecret kakao;

    @ConstructorBinding
    public KakaoProperties(OauthSecret kakao) {
        this.kakao = kakao;
    }

    @Getter
    @Setter
    public static class OauthSecret {
        private String baseUrl;
        private String appKey;
    }

    public String getKakaoBaseUrl() {
        return kakao.getBaseUrl();
    }
    public String getKakaoAppKey() {
        return kakao.getAppKey();
    }
}
