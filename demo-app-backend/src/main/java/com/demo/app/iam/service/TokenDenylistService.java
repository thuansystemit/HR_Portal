package com.demo.app.iam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis-backed JWT denylist for immediate token revocation (AC-12).
 * Denied tokens are stored with a TTL matching their original expiry — Redis
 * auto-evicts them once they would have expired anyway.
 */
@Service
@RequiredArgsConstructor
public class TokenDenylistService {

    private static final String PREFIX = "token:denied:";
    private final StringRedisTemplate redisTemplate;

    public void deny(String jti, Instant expiry) {
        Duration ttl = Duration.between(Instant.now(), expiry);
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(PREFIX + jti, "1", ttl);
        }
    }

    public boolean isDenied(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
    }
}
