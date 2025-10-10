package com.example.speechmate_backend.config.redis;

import com.example.speechmate_backend.common.exception.UploadLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public void deleteRefreshToken(String userId) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
    }

    public void uploadlimit(String userId) {
        String key = "upload:" + userId + ":" + LocalDate.now();
        Long count = redisTemplate.opsForValue().increment(key);
        if(count == 1) {
            long ttl = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay()).getSeconds();
            redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
        }
        if(count > 5) {
            throw UploadLimitExceededException.EXCEPTION;
        }
    }
}
