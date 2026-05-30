package com.demo.app.config;

import com.demo.app.compliance.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CM-6: Validates that all FedRAMP-required security settings are configured at startup.
 * In development (enforce=false): logs warnings for each gap.
 * In production (enforce=true): fails fast with IllegalStateException to prevent
 * a misconfigured instance from serving traffic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityConfigValidator implements ApplicationRunner {

    @Value("${app.encryption.pii-key:}")
    private String piiKey;

    @Value("${app.jwt.keystore.path:}")
    private String keystorePath;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.malware.scan.enabled:false}")
    private boolean malwareScanEnabled;

    @Value("${app.internal.api-key:}")
    private String internalApiKey;

    @Value("${app.security.enforce-config-check:false}")
    private boolean enforceConfigCheck;

    private final AuditService auditService;

    @Override
    public void run(ApplicationArguments args) {
        var violations = collectViolations();

        if (violations.isEmpty()) {
            log.info("CM-6: security configuration baseline check passed");
            auditService.log(null, "SECURITY_CONFIG_CHECK_PASSED", "System", null, null,
                    Map.of("violations", "0"), "success");
            return;
        }

        violations.forEach(v -> log.warn("CM-6 configuration gap: {}", v));
        auditService.log(null, "SECURITY_CONFIG_CHECK_FAILED", "System", null, null,
                Map.of("violations", String.valueOf(violations.size()),
                        "details", String.join("; ", violations)),
                "failure");

        if (enforceConfigCheck) {
            throw new IllegalStateException(
                    "CM-6: startup aborted — " + violations.size() +
                    " security configuration violation(s). Set app.security.enforce-config-check=false to bypass (dev only).");
        }
    }

    List<String> collectViolations() {
        var violations = new ArrayList<String>();
        if (piiKey.isBlank()) {
            violations.add("app.encryption.pii-key not set — CV PII encrypted with ephemeral key; data unreadable after restart (SC-28)");
        }
        if (keystorePath.isBlank()) {
            violations.add("app.jwt.keystore.path not set — JWT signing key ephemeral; sessions invalidated on restart (SC-12)");
        }
        if (!cookieSecure) {
            violations.add("app.cookie.secure=false — auth cookies sent over HTTP; vulnerable to interception (SC-8)");
        }
        if (!malwareScanEnabled) {
            violations.add("app.malware.scan.enabled=false — uploaded files not scanned for malware (SI-3)");
        }
        if (internalApiKey.isBlank()) {
            violations.add("app.internal.api-key not set — internal API endpoints will reject all requests (IA-3)");
        }
        return violations;
    }
}
