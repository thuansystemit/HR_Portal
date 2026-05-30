package com.demo.app.config;

import com.demo.app.compliance.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class SecurityConfigValidatorTest {

    @Mock AuditService auditService;

    SecurityConfigValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SecurityConfigValidator(auditService);
        // fully-configured baseline — individual tests override specific fields
        ReflectionTestUtils.setField(validator, "piiKey", "a".repeat(64));
        ReflectionTestUtils.setField(validator, "keystorePath", "/secrets/jwt.p12");
        ReflectionTestUtils.setField(validator, "cookieSecure", true);
        ReflectionTestUtils.setField(validator, "malwareScanEnabled", true);
        ReflectionTestUtils.setField(validator, "internalApiKey", "strong-key-abc");
        ReflectionTestUtils.setField(validator, "enforceConfigCheck", false);
    }

    private void run() throws Exception {
        validator.run(new DefaultApplicationArguments());
    }

    @Test
    void noViolations_whenAllSettingsConfigured() {
        assertThat(validator.collectViolations()).isEmpty();
    }

    @Test
    void emitsPassedAuditEvent_whenNoViolations() throws Exception {
        run();

        verify(auditService).log(isNull(), eq("SECURITY_CONFIG_CHECK_PASSED"), eq("System"),
                isNull(), isNull(), argThat(m -> "0".equals(m.get("violations"))), eq("success"));
    }

    @Test
    void reportsPiiKeyViolation_whenBlank() {
        ReflectionTestUtils.setField(validator, "piiKey", "");

        assertThat(validator.collectViolations())
                .anyMatch(v -> v.contains("app.encryption.pii-key") && v.contains("SC-28"));
    }

    @Test
    void reportsKeystorePathViolation_whenBlank() {
        ReflectionTestUtils.setField(validator, "keystorePath", "");

        assertThat(validator.collectViolations())
                .anyMatch(v -> v.contains("app.jwt.keystore.path") && v.contains("SC-12"));
    }

    @Test
    void reportsCookieSecureViolation_whenFalse() {
        ReflectionTestUtils.setField(validator, "cookieSecure", false);

        assertThat(validator.collectViolations())
                .anyMatch(v -> v.contains("app.cookie.secure") && v.contains("SC-8"));
    }

    @Test
    void reportsMalwareScanViolation_whenDisabled() {
        ReflectionTestUtils.setField(validator, "malwareScanEnabled", false);

        assertThat(validator.collectViolations())
                .anyMatch(v -> v.contains("app.malware.scan.enabled") && v.contains("SI-3"));
    }

    @Test
    void reportsInternalApiKeyViolation_whenBlank() {
        ReflectionTestUtils.setField(validator, "internalApiKey", "");

        assertThat(validator.collectViolations())
                .anyMatch(v -> v.contains("app.internal.api-key") && v.contains("IA-3"));
    }

    @Test
    void reportsAllFiveViolations_whenNothingConfigured() {
        ReflectionTestUtils.setField(validator, "piiKey", "");
        ReflectionTestUtils.setField(validator, "keystorePath", "");
        ReflectionTestUtils.setField(validator, "cookieSecure", false);
        ReflectionTestUtils.setField(validator, "malwareScanEnabled", false);
        ReflectionTestUtils.setField(validator, "internalApiKey", "");

        assertThat(validator.collectViolations()).hasSize(5);
    }

    @Test
    void emitsFailedAuditEvent_withViolationCount_whenSettingsMissing() throws Exception {
        ReflectionTestUtils.setField(validator, "piiKey", "");
        ReflectionTestUtils.setField(validator, "keystorePath", "");

        run();

        verify(auditService).log(isNull(), eq("SECURITY_CONFIG_CHECK_FAILED"), eq("System"),
                isNull(), isNull(),
                argThat(m -> "2".equals(m.get("violations")) && ((String) m.get("details")).contains("SC-28")),
                eq("failure"));
    }

    @Test
    void doesNotThrow_whenViolationsExistButEnforceIsFalse() throws Exception {
        ReflectionTestUtils.setField(validator, "piiKey", "");

        // should not throw
        run();

        verify(auditService).log(any(), eq("SECURITY_CONFIG_CHECK_FAILED"), any(), any(), any(), any(), any());
    }

    @Test
    void throws_whenEnforceIsTrue_andViolationsExist() {
        ReflectionTestUtils.setField(validator, "piiKey", "");
        ReflectionTestUtils.setField(validator, "enforceConfigCheck", true);

        assertThatThrownBy(this::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CM-6")
                .hasMessageContaining("1");
    }

    @Test
    void doesNotThrow_whenEnforceIsTrue_andNoViolations() throws Exception {
        ReflectionTestUtils.setField(validator, "enforceConfigCheck", true);

        // fully configured — must not throw even with enforce=true
        run();

        verify(auditService).log(any(), eq("SECURITY_CONFIG_CHECK_PASSED"), any(), any(), any(), any(), any());
        verifyNoMoreInteractions(auditService);
    }
}
