package com.example.speechmate_backend.config.security;

import com.example.speechmate_backend.common.exception.InvalidTokenException;
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

    public JwtUtil(@Value("${spring.jwt.secret}")String secret){
        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), Jwts.SIG.HS256.key().build().getAlgorithm());
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
}
