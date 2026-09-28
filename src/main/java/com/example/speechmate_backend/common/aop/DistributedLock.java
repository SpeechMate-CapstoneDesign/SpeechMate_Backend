package com.example.speechmate_backend.common.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Redisson Distributed Lock annotation
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /*
    * 락의 이름
    * */
    String key();

    /*
    * 락의 시간 단위
    * */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 락을 기다리는 시간 (default - 5s)
     * 락 획득을 위해 waitTime 만큼 대기
     */
    long waitTime() default 5L;

    /**
     * 락 임대 시간. 기본 -1 = Redisson 워치독.
     * 워치독은 락을 쥔 스레드가 살아 있는 동안 TTL(기본 30s)을 10초마다 갱신하고,
     * JVM이 죽으면 갱신이 멈춰 30초 뒤 자동 해제된다.
     * 고정 값(예: 30)을 주면 작업이 그보다 오래 걸릴 때 락이 먼저 풀려 다른 요청이 들어온다 —
     * STT처럼 외부 API 폴링으로 수 분 걸릴 수 있는 작업에는 고정 임대가 락이 아니다
     * (DistributedLockAopTest.short_lease_lets_second_caller_in 참고).
     * 작업 시간 상한을 확실히 아는 짧은 작업에만 고정 값을 쓴다.
     */
    long leaseTime() default -1L;
}
