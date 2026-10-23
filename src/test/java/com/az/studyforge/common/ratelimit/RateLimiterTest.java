package com.az.studyforge.common.ratelimit;

import com.az.studyforge.common.ratelimit.RateLimitProperties.KeyType;
import com.az.studyforge.common.ratelimit.RateLimitProperties.Rule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private static final long SEC = 1_000_000_000L;

    private final AtomicLong now = new AtomicLong(0);
    private final RateLimiter limiter = new RateLimiter(now::get);

    private static Rule rule(String name, int capacity, Duration period) {
        return new Rule(name, Set.of("POST"), "/api/x", KeyType.USER, capacity, period);
    }

    @Test
    void deniesAfterCapacity_withRetryAfterRoundedUp() {
        Rule r = rule("upload", 2, Duration.ofMinutes(1));

        assertThat(limiter.tryConsume(r, "u:1")).isEqualTo(new RateLimiter.Decision(true, 1, 0));
        assertThat(limiter.tryConsume(r, "u:1")).isEqualTo(new RateLimiter.Decision(true, 0, 0));
        assertThat(limiter.tryConsume(r, "u:1")).isEqualTo(new RateLimiter.Decision(false, 0, 30));
    }

    @Test
    void keysAreIndependent() {
        Rule r = rule("upload", 1, Duration.ofMinutes(1));

        assertThat(limiter.tryConsume(r, "u:1").allowed()).isTrue();
        assertThat(limiter.tryConsume(r, "u:1").allowed()).isFalse();
        assertThat(limiter.tryConsume(r, "u:2").allowed()).isTrue();
        assertThat(limiter.tryConsume(r, "ip:1.2.3.4").allowed()).isTrue();
    }

    @Test
    void rulesAreIndependent() {
        Rule a = rule("a", 1, Duration.ofMinutes(1));
        Rule b = rule("b", 1, Duration.ofMinutes(1));

        assertThat(limiter.tryConsume(a, "u:1").allowed()).isTrue();
        assertThat(limiter.tryConsume(b, "u:1").allowed()).isTrue();
        assertThat(limiter.tryConsume(a, "u:1").allowed()).isFalse();
    }

    @Test
    void allowsAgainAfterWaiting() {
        Rule r = rule("upload", 1, Duration.ofSeconds(10));
        limiter.tryConsume(r, "u:1");
        assertThat(limiter.tryConsume(r, "u:1").allowed()).isFalse();

        now.set(10 * SEC);

        assertThat(limiter.tryConsume(r, "u:1").allowed()).isTrue();
    }

    @Test
    void retryAfterIsAtLeastOneSecond() {
        Rule r = rule("burst", 1000, Duration.ofSeconds(1));       // hồi 1 token mỗi 1ms
        for (int i = 0; i < 1000; i++) limiter.tryConsume(r, "u:1");

        RateLimiter.Decision d = limiter.tryConsume(r, "u:1");

        assertThat(d.allowed()).isFalse();
        assertThat(d.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void evictIdle_removesOnlyFullUnusedBuckets() {
        Rule r = rule("upload", 2, Duration.ofSeconds(10));
        limiter.tryConsume(r, "u:old");
        now.set(20 * SEC);
        limiter.tryConsume(r, "u:new");

        limiter.evictIdle();

        assertThat(limiter.size()).isEqualTo(1);
    }
}
