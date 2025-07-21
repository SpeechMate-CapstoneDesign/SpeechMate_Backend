package com.example.speechmate_backend.config.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class RedisUtil {

    private final StringRedisTemplate redisTemplate;
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String BLACKLIST_PREFIX = "blacklist:";


    public void storeRefreshToken(String usernum, String refreshToken, long ttlHour) {
        redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + usernum, refreshToken, Duration.ofHours(ttlHour));
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }

    public void addToBlacklist(String token, long ttl) {
        redisTemplate.opsForValue().set(
                BLACKLIST_PREFIX + token,
                "true",
                ttl,
                TimeUnit.SECONDS
        );
    }

    public String getRefreshToken(String userId) {
        return redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + userId);
    }
}
