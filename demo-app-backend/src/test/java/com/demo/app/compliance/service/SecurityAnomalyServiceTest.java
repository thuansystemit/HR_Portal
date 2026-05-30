package com.demo.app.compliance.service;

import com.demo.app.compliance.repository.AuditEventRepository;
import com.demo.app.platform.metrics.SecurityEventRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityAnomalyServiceTest {

    @Mock AuditEventRepository auditEventRepository;
    @Mock AuditService auditService;
    @Mock SecurityEventRecorder securityEventRecorder;

    @InjectMocks
    SecurityAnomalyService anomalyService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(anomalyService, "enabled", true);
        ReflectionTestUtils.setField(anomalyService, "windowSeconds", 300);
        ReflectionTestUtils.setField(anomalyService, "mfaLockoutThreshold", 3);
        ReflectionTestUtils.setField(anomalyService, "ipMismatchThreshold", 5);
        ReflectionTestUtils.setField(anomalyService, "mfaFailureThreshold", 10);
    }

    @Test
    void detectAnomalies_whenDisabled_skipsAllChecks() {
        ReflectionTestUtils.setField(anomalyService, "enabled", false);

        anomalyService.detectAnomalies();

        verifyNoInteractions(auditEventRepository, auditService, securityEventRecorder);
    }

    @Test
    void detectAnomalies_belowAllThresholds_doesNotAlert() {
        when(auditEventRepository.countByActionSince(eq("MFA_LOCKOUT"), any())).thenReturn(2L);
        when(auditEventRepository.countByActionSince(eq("SESSION_IP_MISMATCH"), any())).thenReturn(4L);
        when(auditEventRepository.countByActionSince(eq("MFA_VERIFY_FAILED"), any())).thenReturn(9L);

        anomalyService.detectAnomalies();

        verify(auditService, never()).log(any(), any(), any(), any(), any(), any(), any());
        verify(securityEventRecorder, never()).recordAnomalyDetected();
    }

    @Test
    void detectAnomalies_mfaLockoutAtThreshold_emitsAnomalyAudit() {
        when(auditEventRepository.countByActionSince(eq("MFA_LOCKOUT"), any())).thenReturn(3L);
        when(auditEventRepository.countByActionSince(eq("SESSION_IP_MISMATCH"), any())).thenReturn(0L);
        when(auditEventRepository.countByActionSince(eq("MFA_VERIFY_FAILED"), any())).thenReturn(0L);

        anomalyService.detectAnomalies();

        verify(auditService).log(
                eq(SecurityAnomalyService.SYSTEM_ACTOR),
                eq("SECURITY_ANOMALY_DETECTED"),
                eq("System"),
                isNull(),
                isNull(),
                argThat(m -> "MFA_LOCKOUT".equals(m.get("eventType")) && "3".equals(m.get("count"))),
                eq("failure"));
        verify(securityEventRecorder).recordAnomalyDetected();
    }

    @Test
    void detectAnomalies_ipMismatchAtThreshold_emitsAnomalyAudit() {
        when(auditEventRepository.countByActionSince(eq("MFA_LOCKOUT"), any())).thenReturn(0L);
        when(auditEventRepository.countByActionSince(eq("SESSION_IP_MISMATCH"), any())).thenReturn(5L);
        when(auditEventRepository.countByActionSince(eq("MFA_VERIFY_FAILED"), any())).thenReturn(0L);

        anomalyService.detectAnomalies();

        verify(auditService).log(any(), eq("SECURITY_ANOMALY_DETECTED"), any(), any(), any(),
                argThat(m -> "SESSION_IP_MISMATCH".equals(m.get("eventType"))), any());
        verify(securityEventRecorder).recordAnomalyDetected();
    }

    @Test
    void detectAnomalies_mfaFailureAtThreshold_emitsAnomalyAudit() {
        when(auditEventRepository.countByActionSince(eq("MFA_LOCKOUT"), any())).thenReturn(0L);
        when(auditEventRepository.countByActionSince(eq("SESSION_IP_MISMATCH"), any())).thenReturn(0L);
        when(auditEventRepository.countByActionSince(eq("MFA_VERIFY_FAILED"), any())).thenReturn(10L);

        anomalyService.detectAnomalies();

        verify(auditService).log(any(), eq("SECURITY_ANOMALY_DETECTED"), any(), any(), any(),
                argThat(m -> "MFA_VERIFY_FAILED".equals(m.get("eventType"))), any());
        verify(securityEventRecorder).recordAnomalyDetected();
    }

    @Test
    @SuppressWarnings("unchecked")
    void detectAnomalies_multipleThresholdsBreached_emitsOneEventPerType() {
        when(auditEventRepository.countByActionSince(eq("MFA_LOCKOUT"), any())).thenReturn(5L);
        when(auditEventRepository.countByActionSince(eq("SESSION_IP_MISMATCH"), any())).thenReturn(8L);
        when(auditEventRepository.countByActionSince(eq("MFA_VERIFY_FAILED"), any())).thenReturn(0L);

        anomalyService.detectAnomalies();

        verify(auditService, times(2)).log(any(), eq("SECURITY_ANOMALY_DETECTED"),
                any(), any(), any(), any(Map.class), any());
        verify(securityEventRecorder, times(2)).recordAnomalyDetected();
    }

    @Test
    void detectAnomalies_queriesWithinConfiguredWindow() {
        ReflectionTestUtils.setField(anomalyService, "windowSeconds", 60);
        when(auditEventRepository.countByActionSince(any(), any())).thenReturn(0L);

        var before = Instant.now().minusSeconds(61);
        anomalyService.detectAnomalies();
        var after = Instant.now().minusSeconds(59);

        verify(auditEventRepository, times(3)).countByActionSince(any(),
                argThat(since -> since.isAfter(before) && since.isBefore(after)));
    }

    @Test
    void detectAnomalies_auditContainsThresholdAndWindowDetails() {
        when(auditEventRepository.countByActionSince(eq("MFA_LOCKOUT"), any())).thenReturn(4L);
        when(auditEventRepository.countByActionSince(eq("SESSION_IP_MISMATCH"), any())).thenReturn(0L);
        when(auditEventRepository.countByActionSince(eq("MFA_VERIFY_FAILED"), any())).thenReturn(0L);

        anomalyService.detectAnomalies();

        verify(auditService).log(any(), any(), any(), any(), any(),
                argThat(m -> "3".equals(m.get("threshold")) && "300".equals(m.get("windowSeconds"))),
                any());
    }
}
