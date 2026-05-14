package com.demo.app.platform.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisIdempotencyService implements IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public <T> Optional<T> findCached(UUID userId, String resource, String idempotencyKey, Class<T> responseType) {
        String key = buildKey(userId, resource, idempotencyKey);
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, responseType));
        } catch (Exception e) {
            log.warn("Idempotency cache read failed for key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public <T> void store(UUID userId, String resource, String idempotencyKey, T response) {
        String key = buildKey(userId, resource, idempotencyKey);
        try {
            String json = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(key, json, TTL);
        } catch (Exception e) {
            log.warn("Idempotency cache write failed for key={}: {}", key, e.getMessage());
        }
    }

    private String buildKey(UUID userId, String resource, String idempotencyKey) {
        return "idempotency:" + userId + ":" + resource + ":" + idempotencyKey;
    }
}
