package com.demo.app.iam.service;

import com.demo.app.iam.entity.Credential;
import com.demo.app.iam.entity.MfaPendingSession;
import com.demo.app.iam.entity.User;
import com.demo.app.iam.repository.CredentialRepository;
import com.demo.app.iam.repository.MfaPendingSessionRepository;
import com.demo.app.iam.repository.UserRepository;
import com.demo.app.platform.exception.ForbiddenException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

@Service
@RequiredArgsConstructor
@Slf4j
public class MfaService {

    private static final int CHALLENGE_TTL_SECONDS = 300;
    private static final int ENROLL_TTL_SECONDS    = 600; // 10 minutes to scan QR + confirm
    private static final String ENROLL_PREFIX      = "mfa:enroll:";

    private final CredentialRepository credentialRepository;
    private final MfaPendingSessionRepository pendingSessionRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;

    private final DefaultSecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final CodeGenerator  codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
    private final CodeVerifier   codeVerifier  = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());

    // --- Setup ---

    @Transactional
    public MfaSetupResponse initSetup(UUID userId, String userEmail) {
        var credential = getCredential(userId);
        if (credential.isMfaEnabled()) {
            throw new ForbiddenException("MFA already enrolled");
        }
        var secret = secretGenerator.generate();
        credential.setMfaSecret(secret);
        credentialRepository.save(credential);

        var qrData = new QrData.Builder()
                .label(userEmail)
                .secret(secret)
                .issuer("HRPortal")
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        try {
            QrGenerator generator = new ZxingPngQrGenerator();
            String qrUri = getDataUriForImage(generator.generate(qrData), generator.getImageMimeType());
            return new MfaSetupResponse(secret, qrUri);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    @Transactional
    public List<String> confirmSetup(UUID userId, String totpCode) {
        var credential = getCredential(userId);
        if (credential.isMfaEnabled()) {
            throw new ForbiddenException("MFA already enrolled");
        }
        if (credential.getMfaSecret() == null) {
            throw new ForbiddenException("MFA setup not initiated");
        }
        if (!codeVerifier.isValidCode(credential.getMfaSecret(), totpCode)) {
            throw new ForbiddenException("Invalid TOTP code");
        }
        var backupCodes = generateBackupCodes();
        credential.setMfaEnabled(true);
        credential.setMfaMethod("TOTP");
        credential.setMfaEnrolledAt(Instant.now());
        credential.setMfaBackupCodes(backupCodes.stream().map(this::hashBackupCode).toList());
        credentialRepository.save(credential);
        return backupCodes; // return plaintext once — never stored
    }

    @Transactional
    public void disable(UUID userId, String totpCode) {
        var credential = getCredential(userId);
        if (!credential.isMfaEnabled()) {
            throw new ForbiddenException("MFA not enrolled");
        }
        if (!codeVerifier.isValidCode(credential.getMfaSecret(), totpCode)) {
            throw new ForbiddenException("Invalid TOTP code");
        }
        credential.setMfaEnabled(false);
        credential.setMfaSecret(null);
        credential.setMfaEnrolledAt(null);
        credential.setMfaBackupCodes(null);
        credentialRepository.save(credential);
    }

    // --- Enrollment-from-login (IA-2 hard-block) ---

    /** Creates a short-lived Redis token so an unauthenticated user can complete MFA setup. */
    public String createEnrollmentToken(UUID userId) {
        var token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(ENROLL_PREFIX + token, userId.toString(),
                Duration.ofSeconds(ENROLL_TTL_SECONDS));
        return token;
    }

    /** Phase 1 of enrollment: resolve enrollment token → generate secret + QR. */
    @Transactional
    public MfaSetupResponse initSetupByToken(String enrollToken) {
        var userId = resolveEnrollToken(enrollToken);
        var user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ForbiddenException("User not found"));
        return initSetup(userId, user.getEmail());
    }

    /** Phase 2 of enrollment: verify TOTP, enable MFA, consume enrollment token. */
    @Transactional
    public EnrollConfirmResult confirmSetupByToken(String enrollToken, String totpCode) {
        var userId = resolveEnrollToken(enrollToken);
        var backupCodes = confirmSetup(userId, totpCode);
        redisTemplate.delete(ENROLL_PREFIX + enrollToken); // single-use
        return new EnrollConfirmResult(userId, backupCodes);
    }

    private UUID resolveEnrollToken(String token) {
        var raw = redisTemplate.opsForValue().get(ENROLL_PREFIX + token);
        if (raw == null) throw new ForbiddenException("Invalid or expired enrollment token");
        return UUID.fromString(raw);
    }

    public record EnrollConfirmResult(UUID userId, List<String> backupCodes) {}

    // --- Challenge / Verify ---

    @Transactional
    public String createChallenge(UUID userId, String ipAddress) {
        // Clean up any previous pending sessions for this user
        pendingSessionRepository.deleteExpired(Instant.now());

        var token = UUID.randomUUID().toString();
        pendingSessionRepository.save(MfaPendingSession.builder()
                .userId(userId)
                .challengeToken(token)
                .expiresAt(Instant.now().plusSeconds(CHALLENGE_TTL_SECONDS))
                .ipAddress(ipAddress)
                .build());
        return token;
    }

    @Transactional
    public UUID verifyChallenge(String challengeToken, String code) {
        var session = pendingSessionRepository.findByChallengeToken(challengeToken)
                .orElseThrow(() -> new ForbiddenException("Invalid challenge token"));

        if (session.getExpiresAt().isBefore(Instant.now())) {
            pendingSessionRepository.delete(session);
            throw new ForbiddenException("Challenge expired");
        }

        var credential = getCredential(session.getUserId());

        // Try TOTP first
        if (codeVerifier.isValidCode(credential.getMfaSecret(), code)) {
            pendingSessionRepository.delete(session);
            return session.getUserId();
        }

        // Try backup code
        if (verifyAndConsumeBackupCode(credential, code)) {
            pendingSessionRepository.delete(session);
            return session.getUserId();
        }

        throw new ForbiddenException("Invalid MFA code");
    }

    // --- Helpers ---

    private boolean verifyAndConsumeBackupCode(Credential credential, String code) {
        var hashed = hashBackupCode(code.toUpperCase());
        var codes = credential.getMfaBackupCodes();
        if (codes == null || !codes.contains(hashed)) return false;
        var updated = new ArrayList<>(codes);
        updated.remove(hashed);
        credential.setMfaBackupCodes(updated);
        credentialRepository.save(credential);
        return true;
    }

    private List<String> generateBackupCodes() {
        var rand = new Random(System.currentTimeMillis());
        return IntStream.range(0, 10)
                .mapToObj(i -> {
                    var bytes = new byte[5];
                    rand.nextBytes(bytes);
                    return HexFormat.of().formatHex(bytes).toUpperCase();
                })
                .toList();
    }

    private String hashBackupCode(String code) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Credential getCredential(UUID userId) {
        return credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Credential not found for user", userId));
    }

    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    @Transactional
    public void cleanExpiredChallenges() {
        pendingSessionRepository.deleteExpired(Instant.now());
    }

    public record MfaSetupResponse(String secret, String qrCodeUri) {}
}
