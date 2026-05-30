package com.demo.app.iam.service;

import com.demo.app.platform.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordExpireServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    PasswordExpireService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new PasswordExpireService(redisTemplate);
    }

    @Test
    void createExpireToken_storesUserIdWithTtl() {
        var userId = UUID.randomUUID();

        var token = service.createExpireToken(userId);

        assertThat(token).isNotBlank();
        verify(valueOps).set(eq("pw:expire:" + token), eq(userId.toString()), eq(10L), eq(TimeUnit.MINUTES));
    }

    @Test
    void validateAndConsume_returnsUserId_andDeletesKey() {
        var userId = UUID.randomUUID();
        var token = "some-token";
        when(valueOps.getAndDelete("pw:expire:" + token)).thenReturn(userId.toString());

        var result = service.validateAndConsume(token);

        assertThat(result).isEqualTo(userId);
    }

    @Test
    void validateAndConsume_throwsForbidden_whenTokenMissingOrExpired() {
        when(valueOps.getAndDelete(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.validateAndConsume("bad-token"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("expired");
    }
}
