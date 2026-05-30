package com.demo.app.iam.service;

import com.demo.app.platform.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * IA-5(1)(d): Issues and validates short-lived tokens that authorize a forced password change
 * after the system detects that the user's password has exceeded its maximum age.
 */
@Service
@RequiredArgsConstructor
public class PasswordExpireService {

    private static final String KEY_PREFIX = "pw:expire:";
    private static final long TTL_MINUTES = 10;

    private final StringRedisTemplate redisTemplate;

    public String createExpireToken(UUID userId) {
        var token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + token, userId.toString(),
                TTL_MINUTES, TimeUnit.MINUTES);
        return token;
    }

    public UUID validateAndConsume(String token) {
        var key = KEY_PREFIX + token;
        var userId = redisTemplate.opsForValue().getAndDelete(key);
        if (userId == null) {
            throw new ForbiddenException("Invalid or expired password-change token");
        }
        return UUID.fromString(userId);
    }
}
