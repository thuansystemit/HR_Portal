package com.demo.app.compliance.service;

import com.demo.app.compliance.repository.AuditEventRepository;
import com.demo.app.platform.metrics.SecurityEventRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * IR-4/IR-6: escalates a burst of SECURITY_ANOMALY_DETECTED events to a named
 * incident, emits an IR_INCIDENT_RAISED audit record, increments a Micrometer
 * counter, and optionally fires a webhook (PagerDuty, Slack, etc.) for IR-6
 * reporting. Redis deduplication prevents alert storms within the cooldown window.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentService {

    static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String DEDUP_KEY = "incident:notified:ANOMALY_BURST";

    @Value("${app.incident.enabled:true}")
    private boolean enabled;

    @Value("${app.incident.anomaly-threshold:3}")
    private int anomalyThreshold;

    @Value("${app.incident.window-seconds:900}")
    private int windowSeconds;

    @Value("${app.incident.cooldown-seconds:3600}")
    private int cooldownSeconds;

    @Value("${app.incident.webhook-url:}")
    private String webhookUrl;

    private final AuditEventRepository auditEventRepository;
    private final AuditService auditService;
    private final SecurityEventRecorder securityEventRecorder;
    private final StringRedisTemplate redisTemplate;

    // IR-4: runs every 15 min by default; queries anomaly events from the last window
    @Scheduled(fixedDelayString = "${app.incident.check-interval-ms:900000}")
    public void checkForIncidents() {
        if (!enabled) return;
        var since = Instant.now().minusSeconds(windowSeconds);
        long count = auditEventRepository.countByActionSince("SECURITY_ANOMALY_DETECTED", since);
        if (count < anomalyThreshold) return;

        // Redis dedup — suppress repeat notifications within cooldown window
        if (Boolean.TRUE.equals(redisTemplate.hasKey(DEDUP_KEY))) return;

        Map<String, Object> details = Map.of(
                "anomalyCount",  String.valueOf(count),
                "threshold",     String.valueOf(anomalyThreshold),
                "windowSeconds", String.valueOf(windowSeconds));
        auditService.log(SYSTEM_ACTOR, "IR_INCIDENT_RAISED", "System", null, null, details, "failure");
        securityEventRecorder.recordIncidentRaised();
        redisTemplate.opsForValue().set(DEDUP_KEY, "1", Duration.ofSeconds(cooldownSeconds));

        if (!webhookUrl.isBlank()) {
            sendWebhook(webhookUrl, details);
        }
    }

    // IR-6: fire-and-forget HTTP POST to configured endpoint (PagerDuty, Slack webhook, etc.)
    void sendWebhook(String url, Map<String, Object> payload) {
        try {
            var body = new ObjectMapper().writeValueAsString(payload);
            var request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
            log.info("IR-6: incident webhook notified: {}", url);
        } catch (Exception e) {
            log.warn("IR-6: webhook notification failed ({}): {}", url, e.getMessage());
        }
    }
}
