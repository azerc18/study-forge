package com.az.studyforge.common.ratelimit;

import com.az.studyforge.common.dto.ApiResponse;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.common.ratelimit.RateLimitProperties.Rule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Chạy NGAY SAU bước xác thực JWT (đăng ký trong SecurityConfig) để biết user là ai.
 * Request khớp rule nào thì trừ token của rule đó; hết token → 429 RATE_LIMITED + header Retry-After.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private final RateLimiter limiter;
    private final List<Rule> rules;
    private final JsonMapper jsonMapper;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RateLimitFilter(RateLimiter limiter, List<Rule> rules, JsonMapper jsonMapper) {
        this.limiter = limiter;
        this.rules = List.copyOf(rules);
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !path(request).startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String path = path(request);
        String userId = currentUserId();
        String ip = request.getRemoteAddr();

        int minRemaining = Integer.MAX_VALUE;
        for (Rule rule : rules) {
            if (!rule.matches(method, path, matcher)) continue;

            String key = rule.key() == RateLimitProperties.KeyType.USER && userId != null ? "u:" + userId : "ip:" + ip;
            RateLimiter.Decision decision = limiter.tryConsume(rule, key);
            if (!decision.allowed()) {
                log.info("Rate limited: rule={} key={} {} {}", rule.name(), key, method, path);
                reject(response, rule, decision);
                return;
            }
            minRemaining = Math.min(minRemaining, decision.remaining());
        }

        if (minRemaining != Integer.MAX_VALUE) response.setHeader(REMAINING_HEADER, String.valueOf(minRemaining));
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, Rule rule, RateLimiter.Decision decision) throws IOException {
        response.setStatus(ErrorCode.RATE_LIMITED.getStatus().value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setHeader(REMAINING_HEADER, "0");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ApiResponse.error(ErrorCode.RATE_LIMITED,
                ErrorCode.RATE_LIMITED.getDefaultMessage(),
                Map.of("rule", rule.name(), "retryAfterSeconds", decision.retryAfterSeconds())));
    }

    private static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwt ? jwt.getName() : null;
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
