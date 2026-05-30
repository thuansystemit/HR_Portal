package com.demo.app.platform.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityEventRecorderTest {

    private SimpleMeterRegistry registry;
    private SecurityEventRecorder recorder;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        recorder = new SecurityEventRecorder(registry);
    }

    @Test
    void allCounters_preRegistered_atZero() {
        assertThat(registry.find("security.auth.attempt").tag("outcome", "success").counter()).isNotNull();
        assertThat(registry.find("security.auth.attempt").tag("outcome", "failure").counter()).isNotNull();
        assertThat(registry.find("security.auth.failure").tag("reason", "bad_credentials").counter()).isNotNull();
        assertThat(registry.find("security.auth.failure").tag("reason", "account_locked").counter()).isNotNull();
        assertThat(registry.find("security.auth.failure").tag("reason", "account_inactive").counter()).isNotNull();
        assertThat(registry.find("security.auth.failure").tag("reason", "session_limit").counter()).isNotNull();
        assertThat(registry.find("security.auth.account.lockout").counter()).isNotNull();
        assertThat(registry.find("security.auth.token.issued").counter()).isNotNull();
        assertThat(registry.find("security.auth.token.denied").counter()).isNotNull();
        assertThat(registry.find("security.auth.session.idle").counter()).isNotNull();
        assertThat(registry.find("security.malware.blocked").counter()).isNotNull();
        assertThat(registry.find("security.malware.scan.error").counter()).isNotNull();
        assertThat(registry.find("security.user.sessions.revoked").counter()).isNotNull();
        assertThat(registry.find("security.rate.limit.exceeded").counter()).isNotNull();
        assertThat(registry.find("security.auth.account.unlocked").counter()).isNotNull();
        assertThat(registry.find("security.user.auto.deactivated").counter()).isNotNull();
        assertThat(registry.find("security.mfa.enrolled").counter()).isNotNull();
        assertThat(registry.find("security.mfa.disabled").counter()).isNotNull();
        assertThat(registry.find("security.mfa.verify.failed").counter()).isNotNull();
        assertThat(registry.find("security.mfa.backup.code.used").counter()).isNotNull();
        assertThat(registry.find("security.mfa.replay.attempt").counter()).isNotNull();
        assertThat(registry.find("security.mfa.lockout").counter()).isNotNull();
    }

    @Test
    void recordLoginSuccess_incrementsSuccessCounter() {
        recorder.recordLoginSuccess();
        assertCount("security.auth.attempt", "outcome", "success", 1.0);
    }

    @Test
    void recordLoginFailure_incrementsFailureCounter() {
        recorder.recordLoginFailure();
        assertCount("security.auth.attempt", "outcome", "failure", 1.0);
    }

    @Test
    void recordFailureBadCredentials_incrementsCounter() {
        recorder.recordFailureBadCredentials();
        assertCount("security.auth.failure", "reason", "bad_credentials", 1.0);
    }

    @Test
    void recordFailureAccountLocked_incrementsCounter() {
        recorder.recordFailureAccountLocked();
        assertCount("security.auth.failure", "reason", "account_locked", 1.0);
    }

    @Test
    void recordFailureAccountInactive_incrementsCounter() {
        recorder.recordFailureAccountInactive();
        assertCount("security.auth.failure", "reason", "account_inactive", 1.0);
    }

    @Test
    void recordFailureSessionLimit_incrementsCounter() {
        recorder.recordFailureSessionLimit();
        assertCount("security.auth.failure", "reason", "session_limit", 1.0);
    }

    @Test
    void recordAccountLockout_incrementsCounter() {
        recorder.recordAccountLockout();
        assertSimpleCount("security.auth.account.lockout", 1.0);
    }

    @Test
    void recordTokenIssued_incrementsCounter() {
        recorder.recordTokenIssued();
        assertSimpleCount("security.auth.token.issued", 1.0);
    }

    @Test
    void recordTokenDenied_incrementsCounter() {
        recorder.recordTokenDenied();
        assertSimpleCount("security.auth.token.denied", 1.0);
    }

    @Test
    void recordSessionIdle_incrementsCounter() {
        recorder.recordSessionIdle();
        assertSimpleCount("security.auth.session.idle", 1.0);
    }

    @Test
    void recordMalwareBlocked_incrementsCounter() {
        recorder.recordMalwareBlocked();
        assertSimpleCount("security.malware.blocked", 1.0);
    }

    @Test
    void recordMalwareScanError_incrementsCounter() {
        recorder.recordMalwareScanError();
        assertSimpleCount("security.malware.scan.error", 1.0);
    }

    @Test
    void recordUserSessionsRevoked_incrementsByCount() {
        recorder.recordUserSessionsRevoked(3);
        assertSimpleCount("security.user.sessions.revoked", 3.0);
    }

    @Test
    void recordUserSessionsRevoked_accumulatesAcrossMultipleCalls() {
        recorder.recordUserSessionsRevoked(2);
        recorder.recordUserSessionsRevoked(5);
        assertSimpleCount("security.user.sessions.revoked", 7.0);
    }

    @Test
    void recordRateLimitExceeded_incrementsCounter() {
        recorder.recordRateLimitExceeded();
        assertSimpleCount("security.rate.limit.exceeded", 1.0);
    }

    @Test
    void recordAccountUnlocked_incrementsCounter() {
        recorder.recordAccountUnlocked();
        assertSimpleCount("security.auth.account.unlocked", 1.0);
    }

    @Test
    void recordUserAutoDeactivated_incrementsCounter() {
        recorder.recordUserAutoDeactivated();
        assertSimpleCount("security.user.auto.deactivated", 1.0);
    }

    @Test
    void recordMfaEnrolled_incrementsCounter() {
        recorder.recordMfaEnrolled();
        assertSimpleCount("security.mfa.enrolled", 1.0);
    }

    @Test
    void recordMfaDisabled_incrementsCounter() {
        recorder.recordMfaDisabled();
        assertSimpleCount("security.mfa.disabled", 1.0);
    }

    @Test
    void recordMfaVerifyFailed_incrementsCounter() {
        recorder.recordMfaVerifyFailed();
        assertSimpleCount("security.mfa.verify.failed", 1.0);
    }

    @Test
    void recordMfaBackupCodeUsed_incrementsCounter() {
        recorder.recordMfaBackupCodeUsed();
        assertSimpleCount("security.mfa.backup.code.used", 1.0);
    }

    @Test
    void recordMfaReplayAttempt_incrementsCounter() {
        recorder.recordMfaReplayAttempt();
        assertSimpleCount("security.mfa.replay.attempt", 1.0);
    }

    @Test
    void recordMfaLockout_incrementsCounter() {
        recorder.recordMfaLockout();
        assertSimpleCount("security.mfa.lockout", 1.0);
    }

    private void assertCount(String name, String tagKey, String tagValue, double expected) {
        Counter counter = registry.find(name).tag(tagKey, tagValue).counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(expected);
    }

    private void assertSimpleCount(String name, double expected) {
        Counter counter = registry.find(name).counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(expected);
    }
}
