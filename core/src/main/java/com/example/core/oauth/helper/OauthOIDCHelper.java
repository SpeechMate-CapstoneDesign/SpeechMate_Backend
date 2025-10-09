package com.example.core.oauth.helper;

import com.example.core.oauth.dto.OIDCDecodePayload;
import com.example.core.oauth.dto.OIDCPublickeyDto;
import com.example.core.oauth.dto.OIDCPublickeyResponse;
import com.example.core.oauth.provider.JwtOIDCProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class OauthOIDCHelper {

    private final JwtOIDCProvider jwtOIDCProvider;

    // kid를 토큰에서 가져온다.
    private String getKidFromUnsignedToken(String token, String iss, String aud){
        return jwtOIDCProvider.getKidFromUnsignedTokenHeader(token, iss, aud);
    }

    public OIDCDecodePayload getPayloadFromIdToken(
            String token, String iss, String aud, OIDCPublickeyResponse oidcPublickeyResponse
    ){
        String kid = getKidFromUnsignedToken(token, iss, aud);

        OIDCPublickeyDto key = oidcPublickeyResponse.getKeys().stream()
                .filter(k -> k.getKid().equals(kid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No matching key found"));

        return jwtOIDCProvider.getOIDCTokenBody(token, key.getN(), key.getE(), iss, aud);
    }
}
