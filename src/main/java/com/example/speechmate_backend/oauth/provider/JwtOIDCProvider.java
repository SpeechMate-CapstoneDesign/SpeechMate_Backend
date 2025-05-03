package com.example.speechmate_backend.oauth.provider;

import com.example.speechmate_backend.common.exception.ExpiredTokenException;
import com.example.speechmate_backend.common.exception.InvalidTokenException;
import com.example.speechmate_backend.oauth.dto.OIDCDecodePayload;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class JwtOIDCProvider implements OauthOIDCProvider{

    private final String KID = "kid";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getKidFromUnsignedTokenHeader(String token, String iss, String aud) {
        try {
            String unsignedTokenHeader = getUnsignedToken(token);
            String headerJson = new String(Base64.getUrlDecoder().decode(unsignedTokenHeader));
            Map<String, Object> headerMap = objectMapper.readValue(headerJson, new TypeReference<Map<String, Object>>() {});
            return (String) headerMap.get(KID);
        } catch (Exception e) {
            log.error("Error extracting kid from token header: {}", e.toString());
            throw InvalidTokenException.EXCEPTION;
        }
    }

    // 헤더만 가져옴
    private String getUnsignedToken(String token){
        String[] splitToken = token.split("\\.");
        if(splitToken.length != 3)throw InvalidTokenException.EXCEPTION;
        return splitToken[0];
    }

    @Override
    public OIDCDecodePayload getOIDCTokenBody(String token, String modulus, String exponent, String iss, String aud) {
        Claims payload = getOIDCTokenJws(token, modulus, exponent, iss, aud).getPayload();
        return new OIDCDecodePayload(
                payload.getIssuer(),
                payload.getAudience().toString(),
                payload.getSubject(),
                payload.get("email", String.class));
    }

    private Jws<Claims> getOIDCTokenJws(String token, String modulus, String exponent, String iss, String aud) {
        try {
            return Jwts.parser()
                    .verifyWith(getRSAPublickey(modulus, exponent))
                    .requireIssuer(iss)
                    .requireAudience(aud)
                    .build()
                    .parseSignedClaims(token);
        } catch (ExpiredJwtException e){
            throw ExpiredTokenException.EXCEPTION;
        } catch (Exception e){
            log.error(e.toString());
            throw InvalidTokenException.EXCEPTION;
        }
    }

    /**
     * n, e 조합으로 공개키를 생성하는 메서드
     */
    private PublicKey getRSAPublickey(String modulns, String exponent) throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        byte[] decodeN = Base64.getUrlDecoder().decode(modulns);
        byte[] decodeE = Base64.getUrlDecoder().decode(exponent);
        BigInteger n = new BigInteger(1, decodeN);
        BigInteger e = new BigInteger(1, decodeE);

        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(n, e);
        return keyFactory.generatePublic(keySpec);

    }

}
