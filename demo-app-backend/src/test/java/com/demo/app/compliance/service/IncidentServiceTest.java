package com.demo.app.compliance.service;

import com.demo.app.compliance.repository.AuditEventRepository;
import com.demo.app.platform.metrics.SecurityEventRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock AuditEventRepository auditEventRepository;
    @Mock AuditService auditService;
    @Mock SecurityEventRecorder securityEventRecorder;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    IncidentService service;

    @BeforeEach
    void setUp() {
        service = spy(new IncidentService(auditEventRepository, auditService, securityEventRecorder, redisTemplate));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "anomalyThreshold", 3);
        ReflectionTestUtils.setField(service, "windowSeconds", 900);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 3600);
        ReflectionTestUtils.setField(service, "webhookUrl", "");
    }

    @Test
    void checkForIncidents_whenDisabled_skipsAll() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.checkForIncidents();

        verifyNoInteractions(auditEventRepository, auditService, securityEventRecorder, redisTemplate);
    }

    @Test
    void checkForIncidents_belowThreshold_doesNotRaiseIncident() {
        when(auditEventRepository.countByActionSince(any(), any())).thenReturn(2L);

        service.checkForIncidents();

        verify(auditService, never()).log(any(), any(), any(), any(), any(), any(), any());
        verify(securityEventRecorder, never()).recordIncidentRaised();
    }

    @Test
    void checkForIncidents_atThreshold_raisesIncidentAuditAndCounter() {
        when(auditEventRepository.countByActionSince(any(), any())).thenReturn(3L);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service.checkForIncidents();

        verify(auditService).log(
                eq(IncidentService.SYSTEM_ACTOR),
                eq("IR_INCIDENT_RAISED"),
                eq("System"),
                isNull(), isNull(),
                argThat(m -> "3".equals(m.get("anomalyCount")) && "3".equals(m.get("threshold"))),
                eq("failure"));
        verify(securityEventRecorder).recordIncidentRaised();
    }

    @Test
    void checkForIncidents_atThreshold_setsRedisCooldown() {
        when(auditEventRepository.countByActionSince(any(), any())).thenReturn(5L);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service.checkForIncidents();

        verify(valueOps).set(
                eq("incident:notified:ANOMALY_BURST"),
                eq("1"),
                eq(Duration.ofSeconds(3600)));
    }

    @Test
    void checkForIncidents_whenAlreadyNotified_skipsReNotification() {
        when(auditEventRepository.countByActionSince(any(), any())).thenReturn(10L);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        service.checkForIncidents();

        verify(auditService, never()).log(any(), any(), any(), any(), any(), any(), any());
        verify(securityEventRecorder, never()).recordIncidentRaised();
    }

    @Test
    void checkForIncidents_withWebhookUrl_callsSendWebhook() {
        ReflectionTestUtils.setField(service, "webhookUrl", "http://example.com/hook");
        when(auditEventRepository.countByActionSince(any(), any())).thenReturn(5L);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doNothing().when(service).sendWebhook(anyString(), any());

        service.checkForIncidents();

        verify(service).sendWebhook(eq("http://example.com/hook"), any());
    }

    @Test
    void checkForIncidents_withBlankWebhookUrl_doesNotCallSendWebhook() {
        when(auditEventRepository.countByActionSince(any(), any())).thenReturn(5L);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service.checkForIncidents();

        verify(service, never()).sendWebhook(any(), any());
    }

    @Test
    void sendWebhook_onException_logsAndDoesNotThrow() {
        // "://invalid" has an empty scheme — URI.create throws immediately, entering the catch block
        assertThatCode(() -> service.sendWebhook("://invalid", Map.<String, Object>of("key", "val")))
                .doesNotThrowAnyException();
    }
}
