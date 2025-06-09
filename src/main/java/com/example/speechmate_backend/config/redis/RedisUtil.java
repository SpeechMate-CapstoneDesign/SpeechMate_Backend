package com.example.speechmate_backend.config.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@RequiredArgsConstructor
@Component
public class RedisUtil {

    private final StringRedisTemplate redisTemplate;


    public void storeRefreshToken(String usernum, String refreshToken, long ttlHour) {
        redisTemplate.opsForValue().set("user" + usernum, refreshToken, Duration.ofHours(ttlHour));
    }

}
