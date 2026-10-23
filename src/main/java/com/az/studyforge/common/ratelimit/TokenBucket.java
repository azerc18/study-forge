package com.az.studyforge.common.ratelimit;

import java.time.Duration;

/**
 * Xô token: đầy {@code capacity} token, mỗi request lấy 1 token, token hồi lại đều đặn
 * (capacity token mỗi period). Thời gian tính bằng nano giây do bên ngoài truyền vào → test được.
 */
final class TokenBucket {

    private final int capacity;
    private final long periodNanos;
    private final double tokensPerNano;

    private double tokens;
    private long lastRefill;
    private long lastUsed;

    TokenBucket(int capacity, Duration period, long now) {
        this.capacity = capacity;
        this.periodNanos = period.toNanos();
        this.tokensPerNano = (double) capacity / periodNanos;
        this.tokens = capacity;
        this.lastRefill = now;
        this.lastUsed = now;
    }

    /** Lấy 1 token. Trả 0 nếu được phép, ngược lại số nano giây phải chờ tới khi có token. */
    synchronized long tryConsume(long now) {
        refill(now);
        lastUsed = now;
        if (tokens >= 1) {
            tokens -= 1;
            return 0;
        }
        return Math.max(1, Math.round((1 - tokens) / tokensPerNano));   // round: bỏ sai số số thực
    }

    synchronized int remaining(long now) {
        refill(now);
        return (int) Math.floor(tokens);
    }

    /** Đã hồi đầy và không ai dùng trong 1 period → có thể xoá cho nhẹ bộ nhớ. */
    synchronized boolean isIdle(long now) {
        refill(now);
        return tokens >= capacity && now - lastUsed >= periodNanos;
    }

    private void refill(long now) {
        long elapsed = now - lastRefill;
        if (elapsed <= 0) return;
        tokens = Math.min(capacity, tokens + elapsed * tokensPerNano);
        lastRefill = now;
    }
}
