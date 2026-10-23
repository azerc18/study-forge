package com.az.studyforge.common.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chỉ tạo RateLimiter. RateLimitFilter KHÔNG phải bean (nếu là bean, Spring Boot tự gắn nó vào servlet chain,
 * chạy trước Spring Security nên chưa biết user) → SecurityConfig tự new và gắn sau bước xác thực JWT.
 * Tắt bằng app.rate-limit.enabled=false.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfig {

    @Bean
    RateLimiter rateLimiter() {
        return new RateLimiter(System::nanoTime);
    }
}
