package com.demo.app.iam.security;

import com.demo.app.platform.metrics.SecurityEventRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock SecurityEventRecorder securityEventRecorder;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock FilterChain chain;

    @InjectMocks
    RateLimitFilter filter;

    MockHttpServletRequest request;
    MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setRemoteAddr("10.0.0.1");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // --- pass-through ---

    @Test
    void login_allowsRequest_withinLimit() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/login");
        when(valueOps.increment("ratelimit:login:10.0.0.1")).thenReturn(1L);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
        verify(securityEventRecorder, never()).recordRateLimitExceeded();
    }

    @Test
    void login_allowsRequest_atExactCapacity() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/login");
        when(valueOps.increment("ratelimit:login:10.0.0.1")).thenReturn(5L); // exactly at limit

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void nonRateLimitedPath_passesThrough_withoutRedisCall() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/users");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
        verify(valueOps, never()).increment(any());
        verify(securityEventRecorder, never()).recordRateLimitExceeded();
    }

    @Test
    void getMethod_passesThrough_withoutRedisCall() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/v1/auth/login");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
        verify(valueOps, never()).increment(any());
    }

    // --- 429 responses ---

    @Test
    void login_returns429_whenCountExceedsCapacity() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/login");
        when(valueOps.increment("ratelimit:login:10.0.0.1")).thenReturn(6L); // over limit 5
        when(redisTemplate.getExpire("ratelimit:login:10.0.0.1", TimeUnit.SECONDS)).thenReturn(45L);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("45");
        assertThat(response.getContentAsString()).contains("Too Many Requests");
        assertThat(response.getContentAsString()).contains("45");
        verify(chain, never()).doFilter(any(), any());
        verify(securityEventRecorder).recordRateLimitExceeded();
    }

    @Test
    void mfa_returns429_whenCountExceedsCapacity() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/mfa/verify");
        when(valueOps.increment("ratelimit:mfa:10.0.0.1")).thenReturn(6L);
        when(redisTemplate.getExpire("ratelimit:mfa:10.0.0.1", TimeUnit.SECONDS)).thenReturn(30L);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verify(securityEventRecorder).recordRateLimitExceeded();
    }

    @Test
    void refresh_returns429_whenCountExceedsCapacity() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/refresh");
        when(valueOps.increment("ratelimit:refresh:10.0.0.1")).thenReturn(21L); // over limit 20
        when(redisTemplate.getExpire("ratelimit:refresh:10.0.0.1", TimeUnit.SECONDS)).thenReturn(15L);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verify(securityEventRecorder).recordRateLimitExceeded();
    }

    @Test
    void retryAfterHeader_fallsBackToOne_whenTtlUnavailable() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/login");
        when(valueOps.increment("ratelimit:login:10.0.0.1")).thenReturn(6L);
        when(redisTemplate.getExpire("ratelimit:login:10.0.0.1", TimeUnit.SECONDS)).thenReturn(-1L);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
    }

    // --- MFA shared bucket key ---

    @Test
    void mfaEnrollInit_sharesKey_withMfaVerify() throws ServletException, IOException {
        // Both endpoints → key ratelimit:mfa:ip
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/mfa/enroll/init");
        when(valueOps.increment("ratelimit:mfa:10.0.0.1")).thenReturn(6L);
        when(redisTemplate.getExpire("ratelimit:mfa:10.0.0.1", TimeUnit.SECONDS)).thenReturn(30L);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verify(valueOps).increment("ratelimit:mfa:10.0.0.1");
    }

    // --- IP isolation ---

    @Test
    void differentIps_haveIndependentCounters() throws ServletException, IOException {
        // IP 10.0.0.1 is over limit
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/login");
        when(valueOps.increment("ratelimit:login:10.0.0.1")).thenReturn(6L);
        when(redisTemplate.getExpire("ratelimit:login:10.0.0.1", TimeUnit.SECONDS)).thenReturn(30L);
        filter.doFilterInternal(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(429);

        // IP 10.0.0.2 has its own counter and is within limit
        var req2 = new MockHttpServletRequest();
        req2.setMethod("POST");
        req2.setRequestURI("/api/v1/auth/login");
        req2.setRemoteAddr("10.0.0.2");
        var res2 = new MockHttpServletResponse();
        when(valueOps.increment("ratelimit:login:10.0.0.2")).thenReturn(1L);

        filter.doFilterInternal(req2, res2, chain);

        assertThat(res2.getStatus()).isEqualTo(200);
    }

    @Test
    void xForwardedFor_usedAsClientIp_notRemoteAddr() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        when(valueOps.increment("ratelimit:login:203.0.113.5")).thenReturn(6L);
        when(redisTemplate.getExpire("ratelimit:login:203.0.113.5", TimeUnit.SECONDS)).thenReturn(20L);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verify(valueOps).increment("ratelimit:login:203.0.113.5");
        verify(valueOps, never()).increment("ratelimit:login:10.0.0.1");
    }

    // --- tryConsume unit tests ---

    @Test
    void tryConsume_setsExpiry_onFirstRequest() {
        when(valueOps.increment("ratelimit:login:10.0.0.1")).thenReturn(1L);

        boolean result = filter.tryConsume("login:10.0.0.1", 5, 60);

        assertThat(result).isTrue();
        verify(redisTemplate).expire("ratelimit:login:10.0.0.1", 60, TimeUnit.SECONDS);
    }

    @Test
    void tryConsume_doesNotSetExpiry_onSubsequentRequests() {
        when(valueOps.increment("ratelimit:login:10.0.0.1")).thenReturn(3L);

        boolean result = filter.tryConsume("login:10.0.0.1", 5, 60);

        assertThat(result).isTrue();
        verify(redisTemplate, never()).expire(any(), anyLong(), any());
    }

    @Test
    void tryConsume_returnsFalse_whenCountExceedsCapacity() {
        when(valueOps.increment("ratelimit:login:10.0.0.1")).thenReturn(6L);

        boolean result = filter.tryConsume("login:10.0.0.1", 5, 60);

        assertThat(result).isFalse();
    }
}
