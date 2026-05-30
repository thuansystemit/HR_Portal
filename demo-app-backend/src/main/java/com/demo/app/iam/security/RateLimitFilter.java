package com.demo.app.iam.security;

import com.demo.app.platform.metrics.SecurityEventRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * SC-5: Per-IP fixed-window rate limiter for authentication endpoints backed by Redis.
 *
 * Redis-backed counters are shared across all pods — effective limit is always
 * the configured capacity regardless of horizontal scale.
 * Limits: login/MFA 5 req/60 s; refresh 20 req/60 s.
 * Returns 429 + Retry-After (remaining window TTL) on exhaustion.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // Defaults also serve as field initializers so @InjectMocks in tests gets sensible values
    @Value("${app.rate-limit.login.capacity:5}")         private int loginCapacity       = 5;
    @Value("${app.rate-limit.login.window-seconds:60}")  private int loginWindowSeconds  = 60;
    @Value("${app.rate-limit.mfa.capacity:5}")           private int mfaCapacity         = 5;
    @Value("${app.rate-limit.mfa.window-seconds:60}")    private int mfaWindowSeconds    = 60;
    @Value("${app.rate-limit.refresh.capacity:20}")      private int refreshCapacity     = 20;
    @Value("${app.rate-limit.refresh.window-seconds:60}") private int refreshWindowSeconds = 60;

    private final SecurityEventRecorder securityEventRecorder;
    private final StringRedisTemplate redisTemplate;

    public RateLimitFilter(SecurityEventRecorder securityEventRecorder,
                           StringRedisTemplate redisTemplate) {
        this.securityEventRecorder = securityEventRecorder;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var rateKey = resolveRateKey(request);
        if (rateKey == null) {
            chain.doFilter(request, response);
            return;
        }

        var cfg = capacityFor(rateKey.endpointType());
        if (!tryConsume(rateKey.bucketKey(), cfg.capacity(), cfg.windowSeconds())) {
            long retryAfter = retryAfterSeconds(rateKey.bucketKey());
            log.debug("Rate limit exceeded bucket={} retry-after={}s", rateKey.bucketKey(), retryAfter);
            securityEventRecorder.recordRateLimitExceeded();
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Too Many Requests\",\"retryAfterSeconds\":" + retryAfter + "}");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Increments the Redis counter for this bucket. Sets expiry on the first request in each
     * window. Returns {@code false} when the counter exceeds {@code capacity}.
     *
     * The INCR and EXPIRE are not a single atomic operation. In the extremely rare case where a
     * process crashes between the two calls the key persists until manual eviction — a
     * conservative failure mode (locks the IP out) that is acceptable for auth rate limiting.
     */
    boolean tryConsume(String bucketKey, int capacity, int windowSeconds) {
        String key = "ratelimit:" + bucketKey;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return count == null || count <= capacity;
    }

    private long retryAfterSeconds(String bucketKey) {
        Long ttl = redisTemplate.getExpire("ratelimit:" + bucketKey, TimeUnit.SECONDS);
        return (ttl != null && ttl > 0) ? ttl : 1L;
    }

    private RateKey resolveRateKey(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return null;
        var path = request.getRequestURI();
        var ip = extractClientIp(request);
        return switch (path) {
            case "/api/v1/auth/login"           -> new RateKey("login:" + ip,   EndpointType.LOGIN);
            case "/api/v1/auth/refresh"         -> new RateKey("refresh:" + ip, EndpointType.REFRESH);
            case "/api/v1/auth/mfa/verify",
                 "/api/v1/auth/mfa/enroll/init",
                 "/api/v1/auth/mfa/enroll/confirm" -> new RateKey("mfa:" + ip,  EndpointType.MFA);
            default -> null;
        };
    }

    private BucketConfig capacityFor(EndpointType type) {
        return switch (type) {
            case LOGIN   -> new BucketConfig(loginCapacity,   loginWindowSeconds);
            case MFA     -> new BucketConfig(mfaCapacity,     mfaWindowSeconds);
            case REFRESH -> new BucketConfig(refreshCapacity, refreshWindowSeconds);
        };
    }

    private String extractClientIp(HttpServletRequest request) {
        var forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private enum EndpointType { LOGIN, MFA, REFRESH }

    private record RateKey(String bucketKey, EndpointType endpointType) {}

    private record BucketConfig(int capacity, int windowSeconds) {}
}
