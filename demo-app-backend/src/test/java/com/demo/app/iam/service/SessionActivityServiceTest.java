package com.demo.app.iam.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SessionActivityServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock SetOperations<String, String> setOps;

    private SessionActivityService service;

    private final String JTI     = UUID.randomUUID().toString();
    private final UUID   USER_ID = UUID.randomUUID();

    private static final String ACT_KEY  = "session:activity:";
    private static final String USER_KEY = "user:sessions:";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        service = new SessionActivityService(redisTemplate);
        ReflectionTestUtils.setField(service, "idleTimeoutSeconds", 1800L);
    }

    // --- touch ---

    @Test
    void touch_setsKeyWithConfiguredTtl() {
        service.touch(JTI);

        verify(valueOps).set(ACT_KEY + JTI, "1", Duration.ofSeconds(1800));
    }

    // --- isIdle ---

    @Test
    void isIdle_returnsFalse_whenKeyExists() {
        when(redisTemplate.hasKey(ACT_KEY + JTI)).thenReturn(Boolean.TRUE);

        assertThat(service.isIdle(JTI)).isFalse();
    }

    @Test
    void isIdle_returnsTrue_whenKeyAbsent() {
        when(redisTemplate.hasKey(ACT_KEY + JTI)).thenReturn(null);

        assertThat(service.isIdle(JTI)).isTrue();
    }

    @Test
    void isIdle_returnsTrue_whenRedisReturnsFalse() {
        when(redisTemplate.hasKey(ACT_KEY + JTI)).thenReturn(Boolean.FALSE);

        assertThat(service.isIdle(JTI)).isTrue();
    }

    // --- register ---

    @Test
    void register_addsToUserSet_andTouchesActivityKey() {
        service.register(JTI, USER_ID);

        verify(setOps).add(USER_KEY + USER_ID, JTI);
        verify(valueOps).set(ACT_KEY + JTI, "1", Duration.ofSeconds(1800));
    }

    // --- remove ---

    @Test
    void remove_deletesActivityKey() {
        service.remove(JTI);

        verify(redisTemplate).delete(ACT_KEY + JTI);
    }

    // --- deregister ---

    @Test
    void deregister_removesFromSetAndDeletesActivityKey() {
        service.deregister(JTI, USER_ID);

        verify(setOps).remove(USER_KEY + USER_ID, JTI);
        verify(redisTemplate).delete(ACT_KEY + JTI);
    }

    // --- countActiveSessions ---

    @Test
    void countActiveSessions_returnsZero_whenSetIsEmpty() {
        when(setOps.members(USER_KEY + USER_ID)).thenReturn(Set.of());

        assertThat(service.countActiveSessions(USER_ID)).isZero();
    }

    @Test
    void countActiveSessions_returnsZero_whenSetIsNull() {
        when(setOps.members(USER_KEY + USER_ID)).thenReturn(null);

        assertThat(service.countActiveSessions(USER_ID)).isZero();
    }

    @Test
    void countActiveSessions_returnsActiveCount_andPrunesExpired() {
        String activeJti  = "jti-active";
        String expiredJti = "jti-expired";
        when(setOps.members(USER_KEY + USER_ID)).thenReturn(Set.of(activeJti, expiredJti));
        when(redisTemplate.hasKey(ACT_KEY + activeJti)).thenReturn(Boolean.TRUE);
        when(redisTemplate.hasKey(ACT_KEY + expiredJti)).thenReturn(null);  // expired → null

        int count = service.countActiveSessions(USER_ID);

        assertThat(count).isEqualTo(1);
        verify(setOps).remove(eq(USER_KEY + USER_ID), (Object) eq(expiredJti));
    }

    @Test
    void countActiveSessions_returnsAllActive_whenNoneExpired() {
        String jti1 = "jti-1";
        String jti2 = "jti-2";
        when(setOps.members(USER_KEY + USER_ID)).thenReturn(Set.of(jti1, jti2));
        when(redisTemplate.hasKey(ACT_KEY + jti1)).thenReturn(Boolean.TRUE);
        when(redisTemplate.hasKey(ACT_KEY + jti2)).thenReturn(Boolean.TRUE);

        assertThat(service.countActiveSessions(USER_ID)).isEqualTo(2);
        verify(setOps, never()).remove(any(), any(Object.class));
    }
}
