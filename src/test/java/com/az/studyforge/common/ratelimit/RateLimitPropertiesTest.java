package com.az.studyforge.common.ratelimit;

import com.az.studyforge.common.ratelimit.RateLimitProperties.KeyType;
import com.az.studyforge.common.ratelimit.RateLimitProperties.Rule;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitPropertiesTest {

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Test
    void matches_methodCaseInsensitive_andAntPattern() {
        Rule r = new Rule("exam", Set.of("post"), "/api/quiz-sets/*/exams", KeyType.USER, 5, Duration.ofMinutes(1));

        assertThat(r.matches("POST", "/api/quiz-sets/abc/exams", matcher)).isTrue();
        assertThat(r.matches("GET", "/api/quiz-sets/abc/exams", matcher)).isFalse();
        assertThat(r.matches("POST", "/api/quiz-sets", matcher)).isFalse();
        assertThat(r.matches("POST", "/api/quiz-sets/abc/exams/x", matcher)).isFalse();
    }

    @Test
    void emptyMethods_matchesAnyMethod() {
        Rule r = new Rule("api", null, "/api/**", KeyType.USER, 5, Duration.ofMinutes(1));

        assertThat(r.matches("GET", "/api/documents", matcher)).isTrue();
        assertThat(r.matches("DELETE", "/api/quiz-sets/1", matcher)).isTrue();
    }

    @Test
    void invalidRules_failFastAtStartup() {
        assertThatThrownBy(() -> new Rule("x", null, "/api/**", KeyType.USER, 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Rule("x", null, "/api/**", KeyType.USER, 5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Rule("x", null, " ", KeyType.USER, 5, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Rule(null, null, "/api/**", KeyType.USER, 5, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
