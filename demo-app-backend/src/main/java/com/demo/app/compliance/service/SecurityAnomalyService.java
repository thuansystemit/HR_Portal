package com.demo.app.compliance.service;

import com.demo.app.compliance.repository.AuditEventRepository;
import com.demo.app.platform.metrics.SecurityEventRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * IR-5 / SI-4: detects spikes in security-relevant audit events and emits
 * SECURITY_ANOMALY_DETECTED records so operators and SIEM pipelines are alerted
 * without waiting for a human to review the audit log.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAnomalyService {

    // Stable UUID that identifies system-generated audit entries (no human actor)
    static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Value("${app.security.anomaly.enabled:true}")
    private boolean enabled;

    @Value("${app.security.anomaly.window-seconds:300}")
    private int windowSeconds;

    @Value("${app.security.anomaly.mfa-lockout-threshold:3}")
    private int mfaLockoutThreshold;

    @Value("${app.security.anomaly.ip-mismatch-threshold:5}")
    private int ipMismatchThreshold;

    @Value("${app.security.anomaly.mfa-failure-threshold:10}")
    private int mfaFailureThreshold;

    private final AuditEventRepository auditEventRepository;
    private final AuditService auditService;
    private final SecurityEventRecorder securityEventRecorder;

    @Scheduled(fixedDelayString = "${app.security.anomaly.check-interval-ms:300000}")
    public void detectAnomalies() {
        if (!enabled) return;
        var since = Instant.now().minusSeconds(windowSeconds);
        checkThreshold("MFA_LOCKOUT",         since, mfaLockoutThreshold);
        checkThreshold("SESSION_IP_MISMATCH", since, ipMismatchThreshold);
        checkThreshold("MFA_VERIFY_FAILED",   since, mfaFailureThreshold);
    }

    private void checkThreshold(String eventType, Instant since, int threshold) {
        long count = auditEventRepository.countByActionSince(eventType, since);
        if (count >= threshold) {
            log.warn("Security anomaly: {} events={} threshold={} windowSeconds={}",
                    eventType, count, threshold, windowSeconds);
            auditService.log(SYSTEM_ACTOR, "SECURITY_ANOMALY_DETECTED", "System", null, null,
                    Map.of("eventType",     eventType,
                           "count",         String.valueOf(count),
                           "threshold",     String.valueOf(threshold),
                           "windowSeconds", String.valueOf(windowSeconds)),
                    "failure");
            securityEventRecorder.recordAnomalyDetected();
        }
    }
}
