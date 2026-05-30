package com.demo.app.iam.service;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.iam.entity.Credential;
import com.demo.app.platform.security.encryption.PiiEncryptionConverter;
import jakarta.persistence.Convert;
import com.demo.app.iam.entity.MfaPendingSession;
import com.demo.app.iam.entity.User;
import com.demo.app.iam.repository.CredentialRepository;
import com.demo.app.iam.repository.MfaPendingSessionRepository;
import com.demo.app.iam.repository.UserRepository;
import com.demo.app.platform.exception.ForbiddenException;
import com.demo.app.platform.metrics.SecurityEventRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import dev.samstevens.totp.code.CodeVerifier;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

    @Mock CredentialRepository credentialRepository;
    @Mock MfaPendingSessionRepository pendingSessionRepository;
    @Mock UserRepository userRepository;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;
    @Mock SecurityEventRecorder securityEventRecorder;
    @Mock CodeVerifier codeVerifier;

    private MfaService mfaService;

    private final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        mfaService = new MfaService(credentialRepository, pendingSessionRepository,
                userRepository, redisTemplate, passwordEncoder, auditService, securityEventRecorder);
        ReflectionTestUtils.setField(mfaService, "codeVerifier", codeVerifier);
    }

    // --- createEnrollmentToken ---

    @Test
    void createEnrollmentToken_storesUserIdInRedis_andReturnsToken() {
        var token = mfaService.createEnrollmentToken(USER_ID);

        assertThat(token).isNotBlank();
        verify(valueOps).set(eq("mfa:enroll:" + token), eq(USER_ID.toString()),
                eq(Duration.ofSeconds(600)));
    }

    // --- initSetupByToken ---

    @Test
    void initSetupByToken_throwsForbidden_whenTokenNotFound() {
        when(valueOps.get(startsWith("mfa:enroll:"))).thenReturn(null);

        assertThatThrownBy(() -> mfaService.initSetupByToken("bad-token"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("expired enrollment token");
    }

    @Test
    void initSetupByToken_throwsForbidden_whenUserNotFound() {
        String token = UUID.randomUUID().toString();
        when(valueOps.get("mfa:enroll:" + token)).thenReturn(USER_ID.toString());
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mfaService.initSetupByToken(token))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void initSetupByToken_returnsSetupResponse_whenValid() {
        String token = UUID.randomUUID().toString();
        when(valueOps.get("mfa:enroll:" + token)).thenReturn(USER_ID.toString());

        var user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));

        var credential = new Credential();
        credential.setMfaEnabled(false);
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));

        var response = mfaService.initSetupByToken(token);

        assertThat(response).isNotNull();
        assertThat(response.secret()).isNotBlank();
        assertThat(response.qrCodeUri()).isNotBlank();
    }

    // --- confirmSetupByToken ---

    @Test
    void confirmSetupByToken_throwsForbidden_whenTokenNotFound() {
        when(valueOps.get(startsWith("mfa:enroll:"))).thenReturn(null);

        assertThatThrownBy(() -> mfaService.confirmSetupByToken("bad-token", "123456"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("expired enrollment token");
    }

    // --- createChallenge ---

    @Test
    void createChallenge_savesPendingSession_andReturnsToken() {
        ArgumentCaptor<MfaPendingSession> captor = ArgumentCaptor.forClass(MfaPendingSession.class);

        String token = mfaService.createChallenge(USER_ID, "192.168.1.1");

        assertThat(token).isNotBlank();
        verify(pendingSessionRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getChallengeToken()).isEqualTo(token);
        assertThat(saved.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    }

    // --- verifyChallenge ---

    @Test
    void verifyChallenge_throwsForbidden_whenChallengeNotFound() {
        when(pendingSessionRepository.findByChallengeToken("no-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mfaService.verifyChallenge("no-token", "123456"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Invalid challenge token");
    }

    @Test
    void verifyChallenge_throwsForbidden_whenChallengeExpired() {
        var session = MfaPendingSession.builder()
                .userId(USER_ID)
                .challengeToken("tok")
                .expiresAt(Instant.now().minusSeconds(10))
                .ipAddress("127.0.0.1")
                .build();
        when(pendingSessionRepository.findByChallengeToken("tok")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> mfaService.verifyChallenge("tok", "123456"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("expired");
    }

    // --- cleanExpiredChallenges ---

    @Test
    void cleanExpiredChallenges_callsDeleteExpired() {
        mfaService.cleanExpiredChallenges();

        verify(pendingSessionRepository).deleteExpired(any(Instant.class));
    }

    // --- initSetup error paths ---

    @Test
    void initSetup_throwsForbidden_whenAlreadyEnrolled() {
        var credential = new Credential();
        credential.setMfaEnabled(true);
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> mfaService.initSetup(USER_ID, "user@example.com"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("already enrolled");
    }

    // --- confirmSetup ---

    @Test
    void confirmSetup_throwsForbidden_whenAlreadyEnrolled() {
        var credential = new Credential();
        credential.setMfaEnabled(true);
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> mfaService.confirmSetup(USER_ID, "123456"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("already enrolled");
    }

    @Test
    void confirmSetup_throwsForbidden_whenSetupNotInitiated() {
        var credential = new Credential();
        credential.setMfaEnabled(false);
        credential.setMfaSecret(null);
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> mfaService.confirmSetup(USER_ID, "123456"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("setup not initiated");
    }

    @Test
    void confirmSetup_throwsForbidden_whenCodeInvalid() {
        var credential = new Credential();
        credential.setMfaEnabled(false);
        credential.setMfaSecret("secret");
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("secret", "000000")).thenReturn(false);

        assertThatThrownBy(() -> mfaService.confirmSetup(USER_ID, "000000"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Invalid TOTP code");
    }

    @Test
    void confirmSetup_success_enablesMfaAndReturnsBackupCodes() {
        var credential = new Credential();
        credential.setMfaEnabled(false);
        credential.setMfaSecret("valid-secret");
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("valid-secret", "123456")).thenReturn(true);

        var backupCodes = mfaService.confirmSetup(USER_ID, "123456");

        assertThat(backupCodes).hasSize(10);
        assertThat(credential.isMfaEnabled()).isTrue();
        assertThat(credential.getMfaMethod()).isEqualTo("TOTP");
        assertThat(credential.getMfaEnrolledAt()).isNotNull();
        verify(credentialRepository).save(credential);
    }

    // --- confirmSetupByToken success ---

    @Test
    void confirmSetupByToken_success_consumesToken() {
        String token = UUID.randomUUID().toString();
        when(valueOps.get("mfa:enroll:" + token)).thenReturn(USER_ID.toString());

        var credential = new Credential();
        credential.setMfaEnabled(false);
        credential.setMfaSecret("sec");
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", "111111")).thenReturn(true);

        var result = mfaService.confirmSetupByToken(token, "111111");

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.backupCodes()).hasSize(10);
        verify(redisTemplate).delete("mfa:enroll:" + token);
    }

    // --- disable ---

    @Test
    void disable_throwsForbidden_whenMfaNotEnrolled() {
        var credential = new Credential();
        credential.setMfaEnabled(false);
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> mfaService.disable(USER_ID, "123456"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("MFA not enrolled");
    }

    @Test
    void disable_throwsForbidden_whenCodeInvalid() {
        var credential = new Credential();
        credential.setMfaEnabled(true);
        credential.setMfaSecret("sec");
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", "000000")).thenReturn(false);

        assertThatThrownBy(() -> mfaService.disable(USER_ID, "000000"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Invalid TOTP code");
    }

    @Test
    void disable_success_clearsMfaFields() {
        var credential = new Credential();
        credential.setMfaEnabled(true);
        credential.setMfaSecret("sec");
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", "654321")).thenReturn(true);

        mfaService.disable(USER_ID, "654321");

        assertThat(credential.isMfaEnabled()).isFalse();
        assertThat(credential.getMfaSecret()).isNull();
        verify(credentialRepository).save(credential);
    }

    // --- verifyChallenge success ---

    @Test
    void verifyChallenge_success_withValidTotp() {
        var session = MfaPendingSession.builder()
                .userId(USER_ID)
                .challengeToken("tok")
                .expiresAt(Instant.now().plusSeconds(300))
                .ipAddress("127.0.0.1")
                .build();
        when(pendingSessionRepository.findByChallengeToken("tok")).thenReturn(Optional.of(session));

        var credential = new Credential();
        credential.setMfaSecret("sec");
        credential.setMfaEnabled(true);
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", "111111")).thenReturn(true);

        var result = mfaService.verifyChallenge("tok", "111111");

        assertThat(result).isEqualTo(USER_ID);
        verify(pendingSessionRepository).delete(session);
    }

    @Test
    void verifyChallenge_success_withBackupCode() {
        var session = MfaPendingSession.builder()
                .userId(USER_ID)
                .challengeToken("tok")
                .expiresAt(Instant.now().plusSeconds(300))
                .ipAddress("127.0.0.1")
                .build();
        when(pendingSessionRepository.findByChallengeToken("tok")).thenReturn(Optional.of(session));

        // Compute the SHA-256 hash of "AABBCCDDEE" for the backup code
        var credential = new Credential();
        credential.setMfaSecret("sec");
        credential.setMfaEnabled(true);
        String backupCode = "AABBCCDDEE";
        // Use the same hash logic as MfaService to get the stored hash
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            String hashed = java.util.HexFormat.of().formatHex(
                    digest.digest(backupCode.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            credential.setMfaBackupCodes(new ArrayList<>(List.of(hashed)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", backupCode)).thenReturn(false); // TOTP fails

        var result = mfaService.verifyChallenge("tok", backupCode);

        assertThat(result).isEqualTo(USER_ID);
        assertThat(credential.getMfaBackupCodes()).isEmpty();
        verify(pendingSessionRepository).delete(session);
    }

    @Test
    void verifyChallenge_throwsInvalidCode_whenBothFail() {
        var session = MfaPendingSession.builder()
                .userId(USER_ID)
                .challengeToken("tok")
                .expiresAt(Instant.now().plusSeconds(300))
                .ipAddress("127.0.0.1")
                .build();
        when(pendingSessionRepository.findByChallengeToken("tok")).thenReturn(Optional.of(session));

        var credential = new Credential();
        credential.setMfaSecret("sec");
        credential.setMfaBackupCodes(List.of());
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", "000000")).thenReturn(false);

        assertThatThrownBy(() -> mfaService.verifyChallenge("tok", "000000"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Invalid MFA code");
    }

    // --- SC-13 + SC-28: cryptographic strength and encryption-at-rest tests ---

    @Test
    void confirmSetup_generatesBackupCodesWithSufficientEntropy() {
        var credential = new Credential();
        credential.setMfaEnabled(false);
        credential.setMfaSecret("valid-secret");
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("valid-secret", "123456")).thenReturn(true);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        var codes = mfaService.confirmSetup(USER_ID, "123456");

        assertThat(codes).hasSize(10);
        assertThat(codes).allMatch(code -> code.matches("[0-9A-F]{20}")); // 10 bytes = 80-bit entropy
        assertThat(new java.util.HashSet<>(codes)).hasSize(10); // all distinct
    }

    @Test
    void credential_mfaSecret_isEncryptedAtRest() throws Exception {
        var field = Credential.class.getDeclaredField("mfaSecret");
        var convert = field.getAnnotation(Convert.class);
        assertThat(convert).isNotNull();
        assertThat(convert.converter()).isEqualTo(PiiEncryptionConverter.class);
    }

    // --- AC-7 MFA brute-force lockout tests ---

    @Test
    void verifyChallenge_doesNotLock_whenFailureBelowThreshold() {
        var session = MfaPendingSession.builder()
                .userId(USER_ID).challengeToken("tok")
                .expiresAt(Instant.now().plusSeconds(300)).ipAddress("127.0.0.1").build();
        when(pendingSessionRepository.findByChallengeToken("tok")).thenReturn(Optional.of(session));

        var credential = new Credential();
        credential.setMfaSecret("sec");
        credential.setMfaBackupCodes(List.of());
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", "000000")).thenReturn(false);
        when(valueOps.increment("mfa:fail:" + USER_ID)).thenReturn(3L);

        assertThatThrownBy(() -> mfaService.verifyChallenge("tok", "000000"))
                .isInstanceOf(ForbiddenException.class);

        assertThat(credential.getLockedUntil()).isNull();
        verify(securityEventRecorder, never()).recordMfaLockout();
    }

    @Test
    void verifyChallenge_locksAccount_afterMaxMfaFailures() {
        var session = MfaPendingSession.builder()
                .userId(USER_ID).challengeToken("tok")
                .expiresAt(Instant.now().plusSeconds(300)).ipAddress("127.0.0.1").build();
        when(pendingSessionRepository.findByChallengeToken("tok")).thenReturn(Optional.of(session));

        var credential = new Credential();
        credential.setMfaSecret("sec");
        credential.setMfaBackupCodes(List.of());
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", "000000")).thenReturn(false);
        when(valueOps.increment("mfa:fail:" + USER_ID)).thenReturn(5L);

        assertThatThrownBy(() -> mfaService.verifyChallenge("tok", "000000"))
                .isInstanceOf(ForbiddenException.class);

        assertThat(credential.getLockedUntil()).isNotNull().isAfter(Instant.now());
        verify(credentialRepository, atLeastOnce()).save(credential);
        verify(auditService).log(USER_ID, "MFA_LOCKOUT", "User", USER_ID, null,
                Map.of("attempts", "5"), "failure");
        verify(securityEventRecorder).recordMfaLockout();
    }

    @Test
    void verifyChallenge_setsTtlOnFirstMfaFailure() {
        var session = MfaPendingSession.builder()
                .userId(USER_ID).challengeToken("tok")
                .expiresAt(Instant.now().plusSeconds(300)).ipAddress("127.0.0.1").build();
        when(pendingSessionRepository.findByChallengeToken("tok")).thenReturn(Optional.of(session));

        var credential = new Credential();
        credential.setMfaSecret("sec");
        credential.setMfaBackupCodes(List.of());
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", "000000")).thenReturn(false);
        when(valueOps.increment("mfa:fail:" + USER_ID)).thenReturn(1L);

        assertThatThrownBy(() -> mfaService.verifyChallenge("tok", "000000"))
                .isInstanceOf(ForbiddenException.class);

        verify(redisTemplate).expire(eq("mfa:fail:" + USER_ID), eq(Duration.ofSeconds(900)));
    }

    @Test
    void verifyChallenge_clearsMfaFailures_onTotpSuccess() {
        var session = MfaPendingSession.builder()
                .userId(USER_ID).challengeToken("tok")
                .expiresAt(Instant.now().plusSeconds(300)).ipAddress("127.0.0.1").build();
        when(pendingSessionRepository.findByChallengeToken("tok")).thenReturn(Optional.of(session));

        var credential = new Credential();
        credential.setMfaSecret("sec");
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", "111111")).thenReturn(true);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        mfaService.verifyChallenge("tok", "111111");

        verify(redisTemplate).delete("mfa:fail:" + USER_ID);
    }

    // --- IA-2(8) replay-resistance tests ---

    @Test
    void verifyChallenge_rejectsReplay_whenCodeAlreadyUsed() {
        var session = MfaPendingSession.builder()
                .userId(USER_ID).challengeToken("tok")
                .expiresAt(Instant.now().plusSeconds(300)).ipAddress("127.0.0.1").build();
        when(pendingSessionRepository.findByChallengeToken("tok")).thenReturn(Optional.of(session));

        var credential = new Credential();
        credential.setMfaSecret("sec");
        credential.setMfaBackupCodes(List.of());
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", "123456")).thenReturn(true);
        when(redisTemplate.hasKey("mfa:used:" + USER_ID + ":123456")).thenReturn(true);

        assertThatThrownBy(() -> mfaService.verifyChallenge("tok", "123456"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("already used");

        verify(auditService).log(USER_ID, "MFA_REPLAY_ATTEMPT", "User", USER_ID, null, null, "failure");
        verify(securityEventRecorder).recordMfaReplayAttempt();
    }

    @Test
    void verifyChallenge_marksCodeUsed_afterSuccessfulTotp() {
        var session = MfaPendingSession.builder()
                .userId(USER_ID).challengeToken("tok")
                .expiresAt(Instant.now().plusSeconds(300)).ipAddress("127.0.0.1").build();
        when(pendingSessionRepository.findByChallengeToken("tok")).thenReturn(Optional.of(session));

        var credential = new Credential();
        credential.setMfaSecret("sec");
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", "111111")).thenReturn(true);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        mfaService.verifyChallenge("tok", "111111");

        verify(valueOps).set(eq("mfa:used:" + USER_ID + ":111111"), eq("1"), eq(Duration.ofSeconds(90)));
    }

    @Test
    void confirmSetup_rejectsReplay_whenCodeAlreadyUsed() {
        var credential = new Credential();
        credential.setMfaEnabled(false);
        credential.setMfaSecret("valid-secret");
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("valid-secret", "123456")).thenReturn(true);
        when(redisTemplate.hasKey("mfa:used:" + USER_ID + ":123456")).thenReturn(true);

        assertThatThrownBy(() -> mfaService.confirmSetup(USER_ID, "123456"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("already used");

        verify(auditService).log(USER_ID, "MFA_REPLAY_ATTEMPT", "User", USER_ID, null, null, "failure");
        verify(securityEventRecorder).recordMfaReplayAttempt();
    }

    @Test
    void disable_rejectsReplay_whenCodeAlreadyUsed() {
        var credential = new Credential();
        credential.setMfaEnabled(true);
        credential.setMfaSecret("sec");
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", "654321")).thenReturn(true);
        when(redisTemplate.hasKey("mfa:used:" + USER_ID + ":654321")).thenReturn(true);

        assertThatThrownBy(() -> mfaService.disable(USER_ID, "654321"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("already used");

        verify(auditService).log(USER_ID, "MFA_REPLAY_ATTEMPT", "User", USER_ID, null, null, "failure");
        verify(securityEventRecorder).recordMfaReplayAttempt();
    }

    // --- AU-2 / SI-4 audit and metric tests ---

    @Test
    void confirmSetup_success_emitsAuditEventAndMetric() {
        var credential = new Credential();
        credential.setMfaEnabled(false);
        credential.setMfaSecret("valid-secret");
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("valid-secret", "123456")).thenReturn(true);

        mfaService.confirmSetup(USER_ID, "123456");

        verify(auditService).log(USER_ID, "MFA_ENROLLED", "User", USER_ID, null, null, "success");
        verify(securityEventRecorder).recordMfaEnrolled();
    }

    @Test
    void disable_success_emitsAuditEventAndMetric() {
        var credential = new Credential();
        credential.setMfaEnabled(true);
        credential.setMfaSecret("sec");
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", "654321")).thenReturn(true);

        mfaService.disable(USER_ID, "654321");

        verify(auditService).log(USER_ID, "MFA_DISABLED", "User", USER_ID, null, null, "success");
        verify(securityEventRecorder).recordMfaDisabled();
    }

    @Test
    void verifyChallenge_failure_emitsAuditEventAndMetric() {
        var session = MfaPendingSession.builder()
                .userId(USER_ID)
                .challengeToken("tok")
                .expiresAt(Instant.now().plusSeconds(300))
                .ipAddress("127.0.0.1")
                .build();
        when(pendingSessionRepository.findByChallengeToken("tok")).thenReturn(Optional.of(session));

        var credential = new Credential();
        credential.setMfaSecret("sec");
        credential.setMfaBackupCodes(List.of());
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", "000000")).thenReturn(false);

        assertThatThrownBy(() -> mfaService.verifyChallenge("tok", "000000"))
                .isInstanceOf(ForbiddenException.class);

        verify(auditService).log(USER_ID, "MFA_VERIFY_FAILED", "User", USER_ID, null, null, "failure");
        verify(securityEventRecorder).recordMfaVerifyFailed();
    }

    @Test
    void verifyChallenge_backupCode_emitsAuditEventAndMetric() {
        var session = MfaPendingSession.builder()
                .userId(USER_ID)
                .challengeToken("tok")
                .expiresAt(Instant.now().plusSeconds(300))
                .ipAddress("127.0.0.1")
                .build();
        when(pendingSessionRepository.findByChallengeToken("tok")).thenReturn(Optional.of(session));

        var credential = new Credential();
        credential.setMfaSecret("sec");
        String backupCode = "AABBCCDDEE";
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            String hashed = java.util.HexFormat.of().formatHex(
                    digest.digest(backupCode.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            credential.setMfaBackupCodes(new ArrayList<>(List.of(hashed)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
        when(codeVerifier.isValidCode("sec", backupCode)).thenReturn(false);

        mfaService.verifyChallenge("tok", backupCode);

        verify(auditService).log(USER_ID, "MFA_BACKUP_CODE_USED", "User", USER_ID, null, null, "success");
        verify(securityEventRecorder).recordMfaBackupCodeUsed();
    }
}
