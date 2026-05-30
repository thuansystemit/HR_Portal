package com.demo.app.platform.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Centralises all security-event counters (SI-4).
 * Counters are pre-registered at startup so they appear in /actuator/metrics
 * with count=0 before the first event occurs — important for Prometheus dashboards.
 */
@Component
public class SecurityEventRecorder {

    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Counter failureBadCredentials;
    private final Counter failureAccountLocked;
    private final Counter failureAccountInactive;
    private final Counter failureSessionLimit;
    private final Counter accountLockout;
    private final Counter tokenIssued;
    private final Counter tokenDenied;
    private final Counter sessionIdle;
    private final Counter malwareBlocked;
    private final Counter malwareScanError;
    private final Counter userSessionsRevoked;
    private final Counter rateLimitExceeded;
    private final Counter accountUnlocked;
    private final Counter userAutoDeactivated;
    private final Counter mfaEnrolled;
    private final Counter mfaDisabled;
    private final Counter mfaVerifyFailed;
    private final Counter mfaBackupCodeUsed;
    private final Counter mfaReplayAttempt;
    private final Counter mfaLockout;

    public SecurityEventRecorder(MeterRegistry registry) {
        loginSuccess = Counter.builder("security.auth.attempt")
                .tag("outcome", "success")
                .description("Login attempts where credentials were valid")
                .register(registry);
        loginFailure = Counter.builder("security.auth.attempt")
                .tag("outcome", "failure")
                .description("Login attempts rejected before MFA stage")
                .register(registry);
        failureBadCredentials = Counter.builder("security.auth.failure")
                .tag("reason", "bad_credentials")
                .description("Wrong password")
                .register(registry);
        failureAccountLocked = Counter.builder("security.auth.failure")
                .tag("reason", "account_locked")
                .description("Login blocked — account temporarily locked")
                .register(registry);
        failureAccountInactive = Counter.builder("security.auth.failure")
                .tag("reason", "account_inactive")
                .description("Login blocked — account deactivated or deleted")
                .register(registry);
        failureSessionLimit = Counter.builder("security.auth.failure")
                .tag("reason", "session_limit")
                .description("Token issuance blocked — concurrent session cap reached")
                .register(registry);
        accountLockout = Counter.builder("security.auth.account.lockout")
                .description("Accounts locked after repeated failed login attempts")
                .register(registry);
        tokenIssued = Counter.builder("security.auth.token.issued")
                .description("JWT access tokens successfully issued")
                .register(registry);
        tokenDenied = Counter.builder("security.auth.token.denied")
                .description("Requests rejected due to token on the denylist (AC-12)")
                .register(registry);
        sessionIdle = Counter.builder("security.auth.session.idle")
                .description("Sessions terminated due to inactivity timeout (AC-11)")
                .register(registry);
        malwareBlocked = Counter.builder("security.malware.blocked")
                .description("File uploads blocked after malware detection (SI-3)")
                .register(registry);
        malwareScanError = Counter.builder("security.malware.scan.error")
                .description("Malware scan errors (fail-closed path rejects the upload)")
                .register(registry);
        userSessionsRevoked = Counter.builder("security.user.sessions.revoked")
                .description("Active sessions forcibly revoked on user termination or deactivation (AC-2)")
                .register(registry);
        rateLimitExceeded = Counter.builder("security.rate.limit.exceeded")
                .description("Requests blocked by rate limiter on authentication endpoints (SC-5)")
                .register(registry);
        accountUnlocked = Counter.builder("security.auth.account.unlocked")
                .description("Accounts manually unlocked by an administrator (AC-7)")
                .register(registry);
        userAutoDeactivated = Counter.builder("security.user.auto.deactivated")
                .description("Accounts automatically deactivated due to inactivity (IA-4(e))")
                .register(registry);
        mfaEnrolled = Counter.builder("security.mfa.enrolled")
                .description("MFA successfully enrolled by a user (IA-2)")
                .register(registry);
        mfaDisabled = Counter.builder("security.mfa.disabled")
                .description("MFA disabled for a user account (AU-2)")
                .register(registry);
        mfaVerifyFailed = Counter.builder("security.mfa.verify.failed")
                .description("MFA challenge verification failures (SI-4)")
                .register(registry);
        mfaBackupCodeUsed = Counter.builder("security.mfa.backup.code.used")
                .description("MFA backup codes consumed during login (AU-2)")
                .register(registry);
        mfaReplayAttempt = Counter.builder("security.mfa.replay.attempt")
                .description("TOTP code replay attempts blocked (IA-2(8))")
                .register(registry);
        mfaLockout = Counter.builder("security.mfa.lockout")
                .description("Accounts locked after repeated MFA failures (AC-7)")
                .register(registry);
    }

    public void recordLoginSuccess()            { loginSuccess.increment(); }
    public void recordLoginFailure()            { loginFailure.increment(); }
    public void recordFailureBadCredentials()   { failureBadCredentials.increment(); }
    public void recordFailureAccountLocked()    { failureAccountLocked.increment(); }
    public void recordFailureAccountInactive()  { failureAccountInactive.increment(); }
    public void recordFailureSessionLimit()     { failureSessionLimit.increment(); }
    public void recordAccountLockout()          { accountLockout.increment(); }
    public void recordTokenIssued()             { tokenIssued.increment(); }
    public void recordTokenDenied()             { tokenDenied.increment(); }
    public void recordSessionIdle()             { sessionIdle.increment(); }
    public void recordMalwareBlocked()          { malwareBlocked.increment(); }
    public void recordMalwareScanError()        { malwareScanError.increment(); }
    public void recordUserSessionsRevoked(int count) { userSessionsRevoked.increment(count); }
    public void recordRateLimitExceeded()            { rateLimitExceeded.increment(); }
    public void recordAccountUnlocked()              { accountUnlocked.increment(); }
    public void recordUserAutoDeactivated()          { userAutoDeactivated.increment(); }
    public void recordMfaEnrolled()                  { mfaEnrolled.increment(); }
    public void recordMfaDisabled()                  { mfaDisabled.increment(); }
    public void recordMfaVerifyFailed()              { mfaVerifyFailed.increment(); }
    public void recordMfaBackupCodeUsed()            { mfaBackupCodeUsed.increment(); }
    public void recordMfaReplayAttempt()             { mfaReplayAttempt.increment(); }
    public void recordMfaLockout()                   { mfaLockout.increment(); }
}
