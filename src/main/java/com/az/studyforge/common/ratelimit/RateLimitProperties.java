package com.az.studyforge.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.AntPathMatcher;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cấu hình giới hạn tần suất request (app.rate-limit). Mỗi rule là 1 "xô token" riêng cho mỗi user/IP:
 * tối đa {@code capacity} request, hồi lại dần đều trong {@code period}.
 * Một request khớp nhiều rule thì phải qua TẤT CẢ các rule.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        List<Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /** USER: theo user đăng nhập (chưa đăng nhập thì theo IP). IP: luôn theo IP. */
    public enum KeyType { USER, IP }

    public record Rule(
            String name,
            Set<String> methods,          // rỗng = mọi method
            String path,                  // kiểu Ant: /api/quiz-sets/*/exams, /api/**
            @DefaultValue("USER") KeyType key,
            int capacity,
            Duration period) {

        public Rule {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("rate-limit rule needs a name");
            if (path == null || path.isBlank()) throw new IllegalArgumentException("rule '" + name + "' needs a path");
            if (capacity <= 0) throw new IllegalArgumentException("rule '" + name + "': capacity must be > 0");
            if (period == null || period.isZero() || period.isNegative()) {
                throw new IllegalArgumentException("rule '" + name + "': period must be > 0");
            }
            methods = methods == null ? Set.of()
                    : methods.stream().map(m -> m.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        }

        boolean matches(String method, String requestPath, AntPathMatcher matcher) {
            return (methods.isEmpty() || methods.contains(method)) && matcher.match(path, requestPath);
        }
    }
}
