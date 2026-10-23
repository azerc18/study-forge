package com.az.studyforge.common.ratelimit;

import com.az.studyforge.common.ratelimit.RateLimitProperties.KeyType;
import com.az.studyforge.common.ratelimit.RateLimitProperties.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final AtomicLong now = new AtomicLong(0);
    private final JsonMapper json = JsonMapper.builder().build();

    private final Rule upload = new Rule("upload", Set.of("POST"), "/api/documents", KeyType.USER, 2, Duration.ofHours(1));
    private final Rule auth = new Rule("auth", Set.of("POST"), "/api/auth/**", KeyType.IP, 1, Duration.ofMinutes(1));
    private final Rule api = new Rule("api", Set.of(), "/api/**", KeyType.USER, 100, Duration.ofMinutes(1));

    private final RateLimitFilter filter = new RateLimitFilter(new RateLimiter(now::get), List.of(upload, auth, api), json);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String userId) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(userId).claim("role", "USER").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private MockHttpServletResponse call(String method, String path, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        if (res.getStatus() == 200) assertThat(chain.getRequest()).as("passed to next filter").isNotNull();
        else assertThat(chain.getRequest()).as("blocked").isNull();
        return res;
    }

    @Test
    void underLimit_passesThrough_withRemainingHeader() throws Exception {
        loginAs("user-1");

        MockHttpServletResponse res = call("POST", "/api/documents", "1.1.1.1");

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeader("X-RateLimit-Remaining")).isEqualTo("1");   // min(upload=1, api=99)
    }

    @Test
    void overLimit_returns429Json_withRetryAfter() throws Exception {
        loginAs("user-1");
        call("POST", "/api/documents", "1.1.1.1");
        call("POST", "/api/documents", "1.1.1.1");

        MockHttpServletResponse res = call("POST", "/api/documents", "1.1.1.1");

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("1800");          // 2 token/giờ → 30 phút
        assertThat(res.getContentType()).startsWith("application/json");
        JsonNode body = json.readTree(res.getContentAsString());
        assertThat(body.path("errorCode").asString()).isEqualTo("RATE_LIMITED");
        assertThat(body.path("details").path("rule").asString()).isEqualTo("upload");
        assertThat(body.path("details").path("retryAfterSeconds").asLong()).isEqualTo(1800);
    }

    @Test
    void userKey_isPerUser_evenFromSameIp() throws Exception {
        loginAs("user-1");
        call("POST", "/api/documents", "1.1.1.1");
        call("POST", "/api/documents", "1.1.1.1");
        assertThat(call("POST", "/api/documents", "1.1.1.1").getStatus()).isEqualTo(429);

        loginAs("user-2");
        assertThat(call("POST", "/api/documents", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    void ipKey_isPerIp_evenWhenAnonymous() throws Exception {
        assertThat(call("POST", "/api/auth/google", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/refresh", "1.1.1.1").getStatus()).isEqualTo(429);
        assertThat(call("POST", "/api/auth/google", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void userRule_withoutLogin_fallsBackToIp() throws Exception {
        call("POST", "/api/documents", "3.3.3.3");
        call("POST", "/api/documents", "3.3.3.3");

        assertThat(call("POST", "/api/documents", "3.3.3.3").getStatus()).isEqualTo(429);
        assertThat(call("POST", "/api/documents", "4.4.4.4").getStatus()).isEqualTo(200);
    }

    @Test
    void otherMethodOrPath_notAffectedBySpecificRule() throws Exception {
        loginAs("user-1");
        call("POST", "/api/documents", "1.1.1.1");
        call("POST", "/api/documents", "1.1.1.1");

        assertThat(call("GET", "/api/documents", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/quiz-sets", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    void nonApiPaths_areNotLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse res = call("POST", "/actuator/health", "1.1.1.1");
            assertThat(res.getStatus()).isEqualTo(200);
            assertThat(res.getHeader("X-RateLimit-Remaining")).isNull();
        }
    }

    @Test
    void afterWaiting_allowedAgain() throws Exception {
        loginAs("user-1");
        call("POST", "/api/documents", "1.1.1.1");
        call("POST", "/api/documents", "1.1.1.1");
        assertThat(call("POST", "/api/documents", "1.1.1.1").getStatus()).isEqualTo(429);

        now.addAndGet(Duration.ofMinutes(30).toNanos());

        assertThat(call("POST", "/api/documents", "1.1.1.1").getStatus()).isEqualTo(200);
    }
}
