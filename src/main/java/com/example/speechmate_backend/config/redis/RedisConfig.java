package com.example.speechmate_backend.config.redis;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@EnableCaching
@Configuration
public class RedisConfig {

    // 연결 팩토리는 자동 구성에 맡긴다. spring.data.redis.* 만으로
    // standalone(host/port) · sentinel(sentinel.master/nodes) · cluster(cluster.nodes)를 모두 만들 수 있고,
    // 직접 RedisStandaloneConfiguration을 만들면 그 선택지가 사라진다.
    // 주의: 실제 팩토리는 Lettuce가 아니라 redisson-spring-boot-starter의 RedissonConnectionFactory다 (스타터가 먼저 등록).
    // 그래서 RedisTemplate의 SET/XADD도 Redisson을 타고, 페일오버 추종(센티널 1초 스캔, 클러스터 MOVED 갱신)도 Redisson이 한다.
    // spring.data.redis.lettuce.* 는 적용되지 않는다. (infra/failover/README.md 정정 참고)

    /**
     * Redis가 죽었을 때 빨리 실패하게 한다. 기본은 명령 타임아웃 3초 × 재시도 4회(간격 1.5초) ≈ 16.5초인데,
     * 그동안 톰캣 스레드(그리고 트랜잭션 안이면 DB 커넥션)가 묶인다. 센티널 페일오버는 6초면 끝나므로
     * 그 뒤 들어온 요청은 어차피 성공한다 → 매달린 요청은 빨리 503으로 끝내는 게 낫다.
     * 타임아웃은 spring.data.redis.timeout(2s)을 스타터가 넘겨주고, 재시도 횟수만 여기서 줄인다.
     */
    @Bean
    public RedissonAutoConfigurationCustomizer redissonRetryCustomizer() {
        return config -> {
            if (config.isSentinelConfig()) config.useSentinelServers().setRetryAttempts(1);
            else if (config.isClusterConfig()) config.useClusterServers().setRetryAttempts(1);
            else config.useSingleServer().setRetryAttempts(1);
        };
    }

    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
// ObjectMapper를 커스터마이징하여 JavaTimeModule 등록
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Jackson이 클래스 타입 정보를 JSON에 포함하도록 설정
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.WRAPPER_ARRAY
        );

        // 커스텀 ObjectMapper를 사용하는 GenericJackson2JsonRedisSerializer 생성
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer)) // 커스텀 시리얼라이저 적용
                .entryTtl(Duration.ofMinutes(10));   //캐시 ttl 10분

        return RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory))
                .cacheDefaults(redisCacheConfiguration)
                .build();
    }
}
