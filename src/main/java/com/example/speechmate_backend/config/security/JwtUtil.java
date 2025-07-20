package com.example.speechmate_backend.config.security;

import com.example.speechmate_backend.common.exception.InvalidTokenException;
import com.example.speechmate_backend.config.redis.RedisUtil;
import com.example.speechmate_backend.user.controller.dto.TokenReissueResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final RedisUtil redisUtil;

    public JwtUtil(@Value("${spring.jwt.secret}")String secret, RedisUtil redisUtil){
        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), Jwts.SIG.HS256.key().build().getAlgorithm());
        this.redisUtil = redisUtil;
    }


    public String getCategory(String token){
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("category", String.class);
    }

    public Long getUserId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Long.parseLong(subject);
        } catch (JwtException e) {
            throw InvalidTokenException.EXCEPTION;
        }
    }

    public Boolean isExpired(String token){
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().getExpiration().before(new Date());
    }

    public long getExpiry(String token){
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration().getTime();
        } catch (JwtException e) {
            throw InvalidTokenException.EXCEPTION;
        }
    }

    public String getExpiryFormatted(String token) {
        long expiry = getExpiry(token);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return formatter.format(new Date(expiry));
    }

    public String createJwt(Long userId, String category, long expirationHour){
        ZonedDateTime issuedT = ZonedDateTime.now();
        ZonedDateTime expirateT = issuedT.plusHours(expirationHour);
        var issuedAtDate = Date.from(issuedT.toInstant());
        var expirationDate = Date.from(expirateT.toInstant());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("category", category)
                .issuedAt(issuedAtDate)
                .expiration(expirationDate)
                .signWith(secretKey)
                .compact();
    }

    public TokenReissueResponse reissueToken(String refreshToken) {
        // 1. Refresh Token 유효성 검증
        validateRefreshToken(refreshToken);

        // 2. 사용자 ID 추출
        Long userId = getUserId(refreshToken);

        // 3. Redis에서 저장된 Refresh Token과 비교
        String storedRefreshToken = redisUtil.getRefreshToken(userId.toString());
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw InvalidTokenException.EXCEPTION;
        }

        //기존 블랙리스트에 추가
        addToBlacklist(refreshToken);

        String newAccessToken = createJwt(userId, "access", 1);
        String newRefreshToken = createJwt(userId, "refresh", 24 * 7);

        // 6. 새로운 Refresh Token을 Redis에 저장
        redisUtil.storeRefreshToken(userId.toString(), newRefreshToken, 24 * 7);

        // 7. 응답 생성
        return new TokenReissueResponse(
                newAccessToken,
                getExpiryFormatted(newAccessToken),
                newRefreshToken,
                getExpiryFormatted(newRefreshToken)
        );
    }


    private void validateRefreshToken(String refreshToken) {
        // 토큰이 null이거나 빈 문자열인지 확인
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw InvalidTokenException.EXCEPTION;
        }

        // 토큰이 만료되었는지 확인
        if (isExpired(refreshToken)) {
            throw InvalidTokenException.EXCEPTION;
        }

        // 토큰 카테고리가 refresh인지 확인
        String category = getCategory(refreshToken);
        if (!"refresh".equals(category)) {
            throw InvalidTokenException.EXCEPTION;
        }

        // 블랙리스트에 있는지 확인
        if (isBlacklisted(refreshToken)) {
            throw InvalidTokenException.EXCEPTION;
        }
    }

    private void addToBlacklist(String token) {
        // 사용된 Refresh Token을 블랙리스트에 추가
        // 토큰의 남은 유효 시간만큼 블랙리스트에 보관
        long expiry = getExpiry(token);
        long currentTime = System.currentTimeMillis();
        long ttl = (expiry - currentTime) / 1000; // 초 단위

        if (ttl > 0) {
            redisUtil.addToBlacklist(token, ttl);
        }
    }

    private boolean isBlacklisted(String token) {
        return redisUtil.isBlacklisted(token);
    }
}
