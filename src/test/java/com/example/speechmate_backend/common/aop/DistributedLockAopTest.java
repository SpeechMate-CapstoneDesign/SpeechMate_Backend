package com.example.speechmate_backend.common.aop;

import com.example.speechmate_backend.common.exception.LockAcquisitionFailedException;
import com.example.speechmate_backend.fcm.FirebaseConfig;
import com.example.speechmate_backend.s3.config.S3Config;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.returnzero.ReturnZeroClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DistributedLock AOP의 의미론을 실제 Redis(Redisson) 상대로 검증한다.
 * 핵심 질문: "락 임대 시간보다 작업이 오래 걸리면 락은 여전히 락인가?"
 */
@ActiveProfiles("test")
@SpringBootTest
class DistributedLockAopTest {

    /** 테스트 전용 대상 빈 — 실제 서비스 대신 AOP 동작만 본다.
     *  카운터는 static: 주입되는 건 CGLIB 프록시라 인스턴스 필드로 읽으면 프록시의 빈 필드를 본다. */
    public static class LockProbe {
        static final AtomicInteger inside = new AtomicInteger();   // 동시에 안에 있는 스레드 수
        static final AtomicInteger maxInside = new AtomicInteger(); // 그 최대값 (2 이상이면 락이 겹친 것)
        static final AtomicInteger executed = new AtomicInteger();

        static void reset() { inside.set(0); maxInside.set(0); executed.set(0); }

        private void work(long sleepMs) throws InterruptedException {
            int now = inside.incrementAndGet();
            maxInside.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(sleepMs);
                executed.incrementAndGet();
            } finally {
                inside.decrementAndGet();
            }
        }

        @DistributedLock(key = "'probe:contend:' + #id", waitTime = 1, leaseTime = 10)
        public String contend(String id) throws InterruptedException { work(2000); return "ok"; }

        @DistributedLock(key = "'probe:short:' + #id", waitTime = 5, leaseTime = 1)
        public String shortLease(String id) throws InterruptedException { work(3000); return "ok"; }

        @DistributedLock(key = "'probe:watchdog:' + #id", waitTime = 5)   // leaseTime 기본 -1 = 워치독
        public String watchdog(String id) throws InterruptedException { work(3000); return "ok"; }
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        LockProbe lockProbe() { return new LockProbe(); }
    }

    @Autowired
    private LockProbe probe;

    // 외부 자격증명이 필요한 빈은 다른 테스트와 같은 방식으로 mock
    @MockBean private S3Config s3Config;
    @MockBean private S3UploadPresignedUrlService s3UploadPresignedUrlService;
    @MockBean private FirebaseConfig firebaseConfig;
    @MockBean private ReturnZeroClient returnZeroClient;

    /** 두 스레드를 startGapMs 간격으로 시작해 예외를 모은다 */
    private List<Throwable> runTwo(ThrowingRunnable task, long startGapMs) throws InterruptedException {
        LockProbe.reset();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch done = new CountDownLatch(2);
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    if (idx == 1) Thread.sleep(startGapMs);
                    task.run();
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        pool.shutdown();
        return errors;
    }

    @FunctionalInterface
    interface ThrowingRunnable { void run() throws Throwable; }

    @Test
    @DisplayName("같은 키로 동시에 들어오면 두 번째는 waitTime 안에 못 잡고 409(LockAcquisitionFailedException)를 받는다")
    void second_caller_gets_409_when_lock_is_held() throws InterruptedException {
        List<Throwable> errors = runTwo(() -> probe.contend("a"), 200);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isInstanceOf(LockAcquisitionFailedException.class);
        assertThat(LockProbe.maxInside.get()).isEqualTo(1);
        assertThat(LockProbe.executed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("[함정 기록] 고정 leaseTime(1s)이 작업(3s)보다 짧으면 락이 먼저 풀려 두 번째 호출이 겹쳐 들어온다")
    void short_lease_lets_second_caller_in() throws InterruptedException {
        List<Throwable> errors = runTwo(() -> probe.shortLease("b"), 200);

        assertThat(errors).isEmpty();                       // 아무도 409를 못 받았고
        assertThat(LockProbe.maxInside.get()).isEqualTo(2);     // 두 스레드가 동시에 임계 구역 안에 있었다
        // 첫 스레드의 finally에서 isHeldByCurrentThread()가 false라 unlock을 건너뛰어 예외는 안 난다 (그게 조용해서 더 위험)
    }

    @Test
    @DisplayName("워치독(leaseTime -1)이면 작업이 길어도 락이 유지돼 두 번째 호출은 끝날 때까지 기다린 뒤 순차 실행된다")
    void watchdog_keeps_lock_until_work_finishes() throws InterruptedException {
        List<Throwable> errors = runTwo(() -> probe.watchdog("c"), 200);

        assertThat(errors).isEmpty();                       // waitTime 5s 안에 첫 작업(3s)이 끝나 두 번째도 잡는다
        assertThat(LockProbe.maxInside.get()).isEqualTo(1);     // 겹침 없음
        assertThat(LockProbe.executed.get()).isEqualTo(2);      // 둘 다 순차 실행
    }
}
