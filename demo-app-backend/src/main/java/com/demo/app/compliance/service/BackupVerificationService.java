package com.demo.app.compliance.service;

import com.demo.app.platform.metrics.SecurityEventRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * CP-9: verifies that a recent database backup exists by checking the age of a
 * manifest file written by the external backup script. Exposes status via the
 * Spring Boot actuator health endpoint and emits audit events on the daily check.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupVerificationService implements HealthIndicator {

    // Stable UUID that identifies system-generated audit entries (no human actor)
    static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Value("${app.backup.verification.enabled:false}")
    private boolean enabled;

    @Value("${app.backup.verification.path:}")
    private String backupPath;

    @Value("${app.backup.verification.max-age-hours:25}")
    private int maxAgeHours;

    private final AuditService auditService;
    private final SecurityEventRecorder securityEventRecorder;

    // CP-9: scheduled daily verification — default 04:00 UTC, after typical off-hours backup window
    @Scheduled(cron = "${app.backup.verification.cron:0 0 4 * * ?}")
    public void verifyBackup() {
        if (!enabled) return;
        var h = health();
        if (Status.UP.equals(h.getStatus())) {
            auditService.log(SYSTEM_ACTOR, "BACKUP_VERIFICATION_PASSED", "System", null, null,
                    h.getDetails(), "success");
        } else {
            auditService.log(SYSTEM_ACTOR, "BACKUP_VERIFICATION_FAILED", "System", null, null,
                    h.getDetails(), "failure");
            securityEventRecorder.recordBackupVerificationFailed();
        }
    }

    @Override
    public Health health() {
        if (backupPath.isBlank()) {
            return Health.unknown()
                    .withDetail("reason", "backup path not configured (set app.backup.verification.path)")
                    .build();
        }
        var file = new File(backupPath);
        if (!file.exists()) {
            return Health.down()
                    .withDetail("reason", "backup manifest not found")
                    .withDetail("path", backupPath)
                    .build();
        }
        var lastModified = Instant.ofEpochMilli(file.lastModified());
        long ageHours = Duration.between(lastModified, Instant.now()).toHours();
        if (ageHours > maxAgeHours) {
            return Health.down()
                    .withDetail("reason", "backup is stale")
                    .withDetail("ageHours", ageHours)
                    .withDetail("maxAgeHours", maxAgeHours)
                    .build();
        }
        return Health.up()
                .withDetail("lastBackup", lastModified.toString())
                .withDetail("ageHours", ageHours)
                .build();
    }
}
