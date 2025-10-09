package com.example.core.oauth.helper;

import com.example.core.user.domain.OauthInfo;
import com.example.core.user.domain.OauthProvider;
import com.example.core.oauth.KakaoProperties;
import com.example.core.oauth.client.KakaoOauthClient;
import com.example.core.oauth.dto.OIDCDecodePayload;
import com.example.core.oauth.dto.OIDCPublickeyResponse;
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
