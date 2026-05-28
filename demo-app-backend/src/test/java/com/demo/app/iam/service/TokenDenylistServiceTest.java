package com.demo.app.iam.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TokenDenylistServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private TokenDenylistService service;

    private final String JTI = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new TokenDenylistService(redisTemplate);
    }

    @Test
    void deny_storesKeyWithPositiveTtl() {
        Instant expiry = Instant.now().plusSeconds(900);

        service.deny(JTI, expiry);

        verify(valueOps).set(eq("token:denied:" + JTI), eq("1"), argThat(d -> !d.isNegative() && !d.isZero()));
    }

    @Test
    void deny_doesNotStore_whenExpiryAlreadyPast() {
        Instant expiry = Instant.now().minusSeconds(10);

        service.deny(JTI, expiry);

        verify(valueOps, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void isDenied_returnsTrue_whenKeyExistsInRedis() {
        when(redisTemplate.hasKey("token:denied:" + JTI)).thenReturn(Boolean.TRUE);

        assertThat(service.isDenied(JTI)).isTrue();
    }

    @Test
    void isDenied_returnsFalse_whenKeyAbsent() {
        when(redisTemplate.hasKey("token:denied:" + JTI)).thenReturn(null);

        assertThat(service.isDenied(JTI)).isFalse();
    }

    @Test
    void isDenied_returnsFalse_whenRedisReturnsExplicitFalse() {
        when(redisTemplate.hasKey("token:denied:" + JTI)).thenReturn(Boolean.FALSE);

        assertThat(service.isDenied(JTI)).isFalse();
    }
}
