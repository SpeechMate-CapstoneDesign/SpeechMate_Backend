package com.example.speechmate_backend.oauth.helper;

import com.example.speechmate_backend.domain.OauthInfo;
import com.example.speechmate_backend.domain.OauthProvider;
import com.example.speechmate_backend.oauth.KakaoProperties;
import com.example.speechmate_backend.oauth.client.KakaoOauthClient;
import com.example.speechmate_backend.oauth.dto.OIDCDecodePayload;
import com.example.speechmate_backend.oauth.dto.OIDCPublickeyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoOauthHelper {

    private final KakaoOauthClient kakaoOauthClient;
    private final OauthOIDCHelper oauthOIDCHelper;
    private final KakaoProperties kakaoProperties;

    public OIDCDecodePayload getOIDCDecodePayload(String token){
        OIDCPublickeyResponse oidcPublickeyResponse = kakaoOauthClient.getOIDCPublickeys();
        return oauthOIDCHelper.getPayloadFromIdToken(
                token,
                kakaoProperties.getKakaoBaseUrl(),
                kakaoProperties.getKakaoAppKey(),
                oidcPublickeyResponse
        );
    }



    public OauthInfo getOauthInfoByKakaoIdToken(String idToken){
        OIDCDecodePayload oidcDecodePayload = getOIDCDecodePayload(idToken);
        return OauthInfo.builder()
                .provider(OauthProvider.KAKAO)
                .oid(oidcDecodePayload.getSub())
                .build();
    }
}
