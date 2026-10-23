package com.az.studyforge.common.ratelimit;

import com.az.studyforge.common.ratelimit.RateLimitProperties.Rule;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Giữ các xô token trong bộ nhớ (đủ cho 1 server). Chạy nhiều server thì cần chuyển sang Redis.
 */
public class RateLimiter {

    public record Decision(boolean allowed, int remaining, long retryAfterSeconds) {}

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;

    public RateLimiter(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public Decision tryConsume(Rule rule, String key) {
        long now = nanoTime.getAsLong();
        TokenBucket bucket = buckets.computeIfAbsent(rule.name() + '|' + key,
                k -> new TokenBucket(rule.capacity(), rule.period(), now));

        long waitNanos = bucket.tryConsume(now);
        if (waitNanos == 0) return new Decision(true, bucket.remaining(now), 0);

        long seconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(waitNanos + 999_999_999L)); // làm tròn lên
        return new Decision(false, 0, seconds);
    }

    /** Dọn xô không còn dùng, 5 phút/lần. */
    @Scheduled(fixedDelayString = "PT5M")
    public void evictIdle() {
        long now = nanoTime.getAsLong();
        buckets.values().removeIf(b -> b.isIdle(now));
    }

    int size() {
        return buckets.size();
    }
}
