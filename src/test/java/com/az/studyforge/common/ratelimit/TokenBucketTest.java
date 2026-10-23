package com.az.studyforge.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketTest {

    private static final long SEC = 1_000_000_000L;

    @Test
    void allowsUpToCapacity_thenAsksToWait() {
        TokenBucket bucket = new TokenBucket(3, Duration.ofMinutes(1), 0);

        assertThat(bucket.tryConsume(0)).isZero();
        assertThat(bucket.tryConsume(0)).isZero();
        assertThat(bucket.tryConsume(0)).isZero();
        assertThat(bucket.tryConsume(0)).isEqualTo(20 * SEC);      // 3 token / 60s → 1 token mỗi 20s
    }

    @Test
    void refillsGraduallyOverThePeriod() {
        TokenBucket bucket = new TokenBucket(3, Duration.ofMinutes(1), 0);
        for (int i = 0; i < 3; i++) bucket.tryConsume(0);

        assertThat(bucket.tryConsume(10 * SEC)).isPositive();       // mới hồi 0.5 token
        assertThat(bucket.tryConsume(20 * SEC)).isZero();           // đủ 1 token
        assertThat(bucket.tryConsume(20 * SEC)).isPositive();
    }

    @Test
    void neverRefillsAboveCapacity() {
        TokenBucket bucket = new TokenBucket(2, Duration.ofSeconds(10), 0);

        assertThat(bucket.remaining(3600 * SEC)).isEqualTo(2);
    }

    @Test
    void isIdle_onlyWhenFullAndUnusedForAPeriod() {
        TokenBucket bucket = new TokenBucket(2, Duration.ofSeconds(10), 0);
        bucket.tryConsume(0);

        assertThat(bucket.isIdle(5 * SEC)).isFalse();               // chưa đầy
        assertThat(bucket.isIdle(10 * SEC)).isTrue();               // đầy lại + 10s không dùng
    }
}
