package com.echo.security;

import com.echo.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Set<String> AUTH_ENDPOINTS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/google",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password"
    );

    private static final int AUTH_LIMIT_PER_MINUTE = 5;
    private static final int HEAVY_LIMIT_PER_MINUTE = 30;
    private static final int GENERAL_LIMIT_PER_MINUTE = 120;

    private final ObjectMapper objectMapper;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId = resolveAuthenticatedUserId();
        if (userId != null) {
            MDC.put("userId", userId);
        }

        RateLimitRule rule = resolveRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = buildKey(request, rule.scope(), userId);
        Bucket bucket = resolveBucket(rule.name() + ":" + key, rule.limitPerMinute());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        response.setHeader("X-RateLimit-Limit", String.valueOf(rule.limitPerMinute()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1L,
                    TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
            writeRateLimitResponse(response, retryAfterSeconds);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitRule resolveRule(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith(API_PREFIX)) {
            return null;
        }
        if ("POST".equals(request.getMethod())
                && ("/api/v1/subscription/apple/notify".equals(path) || "/api/v1/webhooks/resend".equals(path))) {
            return null;
        }
        if ("GET".equals(request.getMethod()) && "/api/v1/health".equals(path)) {
            return null;
        }
        if ("POST".equals(request.getMethod()) && AUTH_ENDPOINTS.contains(path)) {
            return new RateLimitRule("auth", AUTH_LIMIT_PER_MINUTE, KeyScope.IP_ONLY);
        }
        if (isHeavyEndpoint(request.getMethod(), path)) {
            return new RateLimitRule("heavy", HEAVY_LIMIT_PER_MINUTE, KeyScope.USER_OR_IP);
        }
        return new RateLimitRule("general", GENERAL_LIMIT_PER_MINUTE, KeyScope.USER_OR_IP);
    }

    private boolean isHeavyEndpoint(String method, String path) {
        if ("GET".equals(method) && ("/api/v1/summary".equals(path) || "/api/v1/ai-insights".equals(path))) {
            return true;
        }
        if ("POST".equals(method)) {
            return "/api/v1/journal/entries".equals(path)
                    || "/api/v1/journal/entries/transcript".equals(path)
                    || "/api/v1/community/posts".equals(path)
                    || PATH_MATCHER.match("/api/v1/coach/sessions/*/messages", path);
        }
        return false;
    }

    private String buildKey(HttpServletRequest request, KeyScope scope, String userId) {
        if (scope == KeyScope.USER_OR_IP && userId != null) {
            return "user:" + userId;
        }
        return "ip:" + resolveClientIp(request);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String cloudflareIp = request.getHeader("CF-Connecting-IP");
        if (StringUtils.hasText(cloudflareIp)) {
            return cloudflareIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private String resolveAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId().toString();
        }
        return null;
    }

    private Bucket resolveBucket(String key, int limitPerMinute) {
        return buckets.get(key, ignored -> Bucket.builder()
                .addLimit(Bandwidth.classic(
                        limitPerMinute,
                        Refill.intervally(limitPerMinute, Duration.ofMinutes(1))
                ))
                .build());
    }

    private void writeRateLimitResponse(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ErrorResponse.of("RATE_LIMIT_EXCEEDED", "Too many requests. Please retry later.")
        ));
    }

    private record RateLimitRule(String name, int limitPerMinute, KeyScope scope) {}

    private enum KeyScope {
        IP_ONLY,
        USER_OR_IP
    }
}
