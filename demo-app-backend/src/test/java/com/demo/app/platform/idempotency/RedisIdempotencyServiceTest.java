package com.demo.app.platform.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisIdempotencyServiceTest {

    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RedisIdempotencyService service;

    private final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final String IDEM_KEY = "test-key-abc";
    private final String EXPECTED_REDIS_KEY =
            "idempotency:" + USER_ID + ":category:" + IDEM_KEY;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new RedisIdempotencyService(stringRedisTemplate, objectMapper);
    }

    @Test
    void findCached_returnsEmpty_whenKeyNotInRedis() {
        when(valueOperations.get(EXPECTED_REDIS_KEY)).thenReturn(null);

        var result = service.findCached(USER_ID, "category", IDEM_KEY, TestResponse.class);

        assertThat(result).isEmpty();
        verify(valueOperations).get(EXPECTED_REDIS_KEY);
    }

    @Test
    void findCached_returnsCached_whenKeyExists() throws Exception {
        var cached = new TestResponse("hello", 42);
        String json = objectMapper.writeValueAsString(cached);
        when(valueOperations.get(EXPECTED_REDIS_KEY)).thenReturn(json);

        var result = service.findCached(USER_ID, "category", IDEM_KEY, TestResponse.class);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("hello");
        assertThat(result.get().value()).isEqualTo(42);
    }

    @Test
    void findCached_returnsEmpty_whenDeserializationFails() {
        when(valueOperations.get(EXPECTED_REDIS_KEY)).thenReturn("not-valid-json{{{{");

        var result = service.findCached(USER_ID, "category", IDEM_KEY, TestResponse.class);

        assertThat(result).isEmpty();
        // no exception propagated — fail-open
    }

    @Test
    void store_writesToRedisWithTtl() throws Exception {
        var response = new TestResponse("world", 99);

        service.store(USER_ID, "category", IDEM_KEY, response);

        String expectedJson = objectMapper.writeValueAsString(response);
        verify(valueOperations).set(EXPECTED_REDIS_KEY, expectedJson, Duration.ofHours(24));
    }

    // minimal DTO for testing serialization round-trips
    record TestResponse(String name, int value) {}
}
