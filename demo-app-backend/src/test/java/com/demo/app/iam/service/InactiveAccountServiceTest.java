package com.demo.app.iam.service;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.config.JwtConfig;
import com.demo.app.iam.entity.User;
import com.demo.app.iam.repository.UserRepository;
import com.demo.app.platform.metrics.SecurityEventRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InactiveAccountServiceTest {

    @Mock UserRepository userRepository;
    @Mock AuditService auditService;
    @Mock SecurityEventRecorder securityEventRecorder;
    @Mock SessionActivityService sessionActivityService;
    @Mock TokenDenylistService tokenDenylistService;
    @Mock JwtConfig jwtConfig;

    @InjectMocks
    InactiveAccountService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "inactivityDays", 90);
    }

    // --- no-op when nothing to deactivate ---

    @Test
    void deactivateInactiveAccounts_noOp_whenNoInactiveAccounts() {
        when(userRepository.findActiveUsersInactiveSince(any())).thenReturn(List.of());

        service.deactivateInactiveAccounts();

        verify(userRepository, never()).save(any());
        verify(auditService, never()).log(any(), anyString(), anyString(), any(), any(), any(), anyString());
        verify(securityEventRecorder, never()).recordUserAutoDeactivated();
    }

    // --- deactivation of inactive accounts ---

    @Test
    void deactivateInactiveAccounts_setsStatusToInactive() {
        var user = User.builder().id(UUID.randomUUID()).email("idle@example.com")
                .status("active").fullName("Idle User").build();
        when(userRepository.findActiveUsersInactiveSince(any())).thenReturn(List.of(user));
        when(sessionActivityService.getSessionJtis(user.getId())).thenReturn(Set.of());

        service.deactivateInactiveAccounts();

        assertThat(user.getStatus()).isEqualTo("inactive");
        verify(userRepository).save(user);
    }

    @Test
    void deactivateInactiveAccounts_emitsAuditEvent() {
        var userId = UUID.randomUUID();
        var user = User.builder().id(userId).email("idle@example.com")
                .status("active").fullName("Idle User").build();
        when(userRepository.findActiveUsersInactiveSince(any())).thenReturn(List.of(user));
        when(sessionActivityService.getSessionJtis(userId)).thenReturn(Set.of());

        service.deactivateInactiveAccounts();

        verify(auditService).log(
                isNull(),
                eq("USER_AUTO_DEACTIVATED"),
                eq("User"),
                eq(userId),
                any(),
                any(),
                eq("success"));
    }

    @Test
    void deactivateInactiveAccounts_recordsMetricPerUser() {
        var user1 = User.builder().id(UUID.randomUUID()).status("active").fullName("A").email("a@x.com").build();
        var user2 = User.builder().id(UUID.randomUUID()).status("active").fullName("B").email("b@x.com").build();
        when(userRepository.findActiveUsersInactiveSince(any())).thenReturn(List.of(user1, user2));
        when(sessionActivityService.getSessionJtis(any())).thenReturn(Set.of());

        service.deactivateInactiveAccounts();

        verify(securityEventRecorder, times(2)).recordUserAutoDeactivated();
    }

    // --- session revocation ---

    @Test
    void deactivateInactiveAccounts_revokesActiveSessions() {
        var userId = UUID.randomUUID();
        var user = User.builder().id(userId).status("active").fullName("Idle").email("i@x.com").build();
        var jti1 = "jti-111";
        var jti2 = "jti-222";

        when(userRepository.findActiveUsersInactiveSince(any())).thenReturn(List.of(user));
        when(jwtConfig.getAccessExpirySeconds()).thenReturn(900L);
        when(sessionActivityService.getSessionJtis(userId)).thenReturn(Set.of(jti1, jti2));

        service.deactivateInactiveAccounts();

        verify(tokenDenylistService).deny(eq(jti1), eq(900L));
        verify(tokenDenylistService).deny(eq(jti2), eq(900L));
        verify(sessionActivityService).remove(jti1);
        verify(sessionActivityService).remove(jti2);
        verify(sessionActivityService).clearUserSessions(userId);
    }

    @Test
    void deactivateInactiveAccounts_recordsSessionRevocationMetric() {
        var userId = UUID.randomUUID();
        var user = User.builder().id(userId).status("active").fullName("Idle").email("i@x.com").build();
        when(userRepository.findActiveUsersInactiveSince(any())).thenReturn(List.of(user));
        when(jwtConfig.getAccessExpirySeconds()).thenReturn(900L);
        when(sessionActivityService.getSessionJtis(userId)).thenReturn(Set.of("jti-1", "jti-2"));

        service.deactivateInactiveAccounts();

        verify(securityEventRecorder).recordUserSessionsRevoked(2);
    }

    @Test
    void deactivateInactiveAccounts_noSessionRevocation_whenNoActiveSessions() {
        var userId = UUID.randomUUID();
        var user = User.builder().id(userId).status("active").fullName("Idle").email("i@x.com").build();
        when(userRepository.findActiveUsersInactiveSince(any())).thenReturn(List.of(user));
        when(sessionActivityService.getSessionJtis(userId)).thenReturn(Set.of());

        service.deactivateInactiveAccounts();

        verify(tokenDenylistService, never()).deny(anyString(), anyLong());
        verify(securityEventRecorder, never()).recordUserSessionsRevoked(anyInt());
    }

    // --- cutoff calculation ---

    @Test
    void deactivateInactiveAccounts_passesCutoffMatchingInactivityDays() {
        when(userRepository.findActiveUsersInactiveSince(any())).thenReturn(List.of());

        var before = Instant.now().minusSeconds(90L * 86_400).minusSeconds(5);
        service.deactivateInactiveAccounts();
        var after  = Instant.now().minusSeconds(90L * 86_400).plusSeconds(5);

        verify(userRepository).findActiveUsersInactiveSince(
                argThat(cutoff -> cutoff.isAfter(before) && cutoff.isBefore(after)));
    }
}
