package com.example.speechmate_backend.common.aop;

import com.example.speechmate_backend.common.exception.LockAcquisitionFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @DistributedLock 선언 시 수행되는 Aop class
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedLockAop {

    private static final String REDISSON_LOCK_PREFIX = "LOCK:";

    private final RedissonClient redissonClient;
    private final AopForTransaction aopForTransaction;

    @Around("@annotation(com.example.speechmate_backend.common.aop.DistributedLock)")
    public Object lock(final ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        String key = REDISSON_LOCK_PREFIX + CustomSpringELParser.getDynamicValue(signature.getParameterNames(), joinPoint.getArgs(), distributedLock.key());
        RLock rLock = redissonClient.getLock(key);

        try {
            boolean available = rLock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());  // (2)
            if (!available) {
                // 기존 'return false'는 반환 타입이 Boolean이 아닌 메서드에서 ClassCastException을 유발
                throw LockAcquisitionFailedException.EXCEPTION;
            }

            return aopForTransaction.proceed(joinPoint);  // (3)
        } catch (InterruptedException e) {
            throw new InterruptedException();
        } finally {
            try {
                if (rLock.isHeldByCurrentThread()) {
                    rLock.unlock();   // (4)
                }
            } catch (Exception e) {
                // Redis 장애(페일오버 등) 중 unlock 실패 — 워치독 갱신이 멈추면 TTL(30s) 만료로 자동 해제되므로 로그만 남김
                log.warn("Redisson unlock 실패 serviceName: {}, key: {}, cause: {}",
                        method.getName(), key, e.getMessage());
            }
        }
    }
}
