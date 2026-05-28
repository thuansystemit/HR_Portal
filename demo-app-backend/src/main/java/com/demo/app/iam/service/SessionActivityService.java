package com.demo.app.iam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionActivityService {

    private static final String PREFIX = "session:activity:";
    private static final String USER_SESSIONS_PREFIX = "user:sessions:";

    @Value("${app.session.idle-timeout-seconds:1800}")
    private long idleTimeoutSeconds;

    private final StringRedisTemplate redisTemplate;

    /** Register a new session for a user and start the idle clock. Call once at token issuance. */
    public void register(String jti, UUID userId) {
        redisTemplate.opsForSet().add(USER_SESSIONS_PREFIX + userId, jti);
        touch(jti);
    }

    /** Reset the idle TTL for an existing session on each authenticated request. */
    public void touch(String jti) {
        redisTemplate.opsForValue().set(PREFIX + jti, "1", Duration.ofSeconds(idleTimeoutSeconds));
    }

    public boolean isIdle(String jti) {
        return !Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
    }

    /**
     * Count active sessions for a user. Prunes expired jtis from the user's session set
     * so the set stays clean over time.
     */
    public int countActiveSessions(UUID userId) {
        var setKey = USER_SESSIONS_PREFIX + userId;
        Set<String> members = redisTemplate.opsForSet().members(setKey);
        if (members == null || members.isEmpty()) return 0;

        var expired = members.stream()
                .filter(jti -> !Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti)))
                .toArray(String[]::new);

        if (expired.length > 0) {
            redisTemplate.opsForSet().remove(setKey, (Object[]) expired);
        }
        return members.size() - expired.length;
    }

    /** Remove a session's activity key and deregister from the user's session set. */
    public void deregister(String jti, UUID userId) {
        redisTemplate.opsForSet().remove(USER_SESSIONS_PREFIX + userId, jti);
        remove(jti);
    }

    /** Remove only the activity key (used when user ID is unavailable). */
    public void remove(String jti) {
        redisTemplate.delete(PREFIX + jti);
    }
}
