package com.demo.app.iam.service;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.iam.dto.AuthResponse;
import com.demo.app.iam.dto.ChangePasswordRequest;
import com.demo.app.iam.dto.ForceChangePasswordRequest;
import com.demo.app.platform.metrics.SecurityEventRecorder;
import com.demo.app.iam.dto.LoginRequest;
import com.demo.app.iam.dto.UserInfo;
import com.demo.app.iam.entity.Credential;
import com.demo.app.iam.entity.RefreshToken;
import com.demo.app.iam.repository.*;
import com.demo.app.platform.exception.ForbiddenException;
import com.demo.app.platform.exception.PasswordPolicyException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import com.demo.app.platform.exception.WrongPasswordException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PermissionCacheService permissionCacheService;
    private final TokenDenylistService tokenDenylistService;
    private final SessionActivityService sessionActivityService;
    private final MfaService mfaService;
    private final SecurityEventRecorder securityEventRecorder;
    private final PasswordPolicyService passwordPolicyService;
    private final PasswordHistoryService passwordHistoryService;
    private final PasswordExpireService passwordExpireService;
    private final AuditService auditService;
    private final AccessHoursEnforcer accessHoursEnforcer;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.jwt.access-expiry-seconds:900}")
    private int accessExpiry;

    @Value("${app.jwt.refresh-expiry-seconds:604800}")
    private int refreshExpiry;

    @Value("${app.session.max-concurrent:5}")
    private int maxConcurrentSessions;

    @Value("${app.session.ip-binding.enabled:false}")
    private boolean ipBindingEnabled;

    @Value("${app.password.max-age-days:60}")
    private int passwordMaxAgeDays = 60;

    @Value("${app.password.min-age-days:1}")
    private int passwordMinAgeDays = 1;

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletResponse response,
                              String ipAddress, String userAgent) {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new ForbiddenException("Invalid credentials"));

        if (!"active".equals(user.getStatus())) {
            securityEventRecorder.recordLoginFailure();
            securityEventRecorder.recordFailureAccountInactive();
            throw new ForbiddenException("Account is inactive");
        }

        var credential = credentialRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ForbiddenException("Invalid credentials"));

        if (credential.getLockedUntil() != null && credential.getLockedUntil().isAfter(Instant.now())) {
            securityEventRecorder.recordLoginFailure();
            securityEventRecorder.recordFailureAccountLocked();
            throw new ForbiddenException("Account is temporarily locked");
        }

        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            securityEventRecorder.recordLoginFailure();
            securityEventRecorder.recordFailureBadCredentials();
            handleFailedLogin(credential);
            throw new ForbiddenException("Invalid credentials");
        }

        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        // AC-9: preserve previous login time before overwriting so it can be shown on next session
        credential.setPreviousLoginAt(credential.getLastLoginAt());
        credential.setLastLoginAt(Instant.now());
        credentialRepository.save(credential);
        securityEventRecorder.recordLoginSuccess();

        // AC-2(11): deny logins outside the permitted UTC access window
        accessHoursEnforcer.enforce(user.getId());

        // IA-2 hard-block: unenrolled users must complete MFA setup before getting tokens
        if (!credential.isMfaEnabled()) {
            return AuthResponse.mfaEnrollmentRequired(mfaService.createEnrollmentToken(user.getId()));
        }

        // IA-2(1)/IA-2(2): MFA enrolled — require challenge verification before issuing tokens
        return AuthResponse.mfaChallenge(mfaService.createChallenge(user.getId(), ipAddress));
    }

    /** Called by MfaController after successful MFA challenge verification. */
    @Transactional
    public AuthResponse issueTokensForUser(UUID userId, HttpServletRequest request,
                                           HttpServletResponse response) {
        var user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // AC-10: enforce concurrent session limit
        if (sessionActivityService.countActiveSessions(userId) >= maxConcurrentSessions) {
            securityEventRecorder.recordLoginFailure();
            securityEventRecorder.recordFailureSessionLimit();
            throw new ForbiddenException("Maximum concurrent sessions reached");
        }

        // IA-5(1)(f)+(d): block token issuance when a forced change is required (new account or admin reset)
        // or when the password has exceeded its maximum age
        var credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Credential not found"));
        if (credential.isMustChangePassword() || isPasswordExpired(credential)) {
            return AuthResponse.passwordExpired(passwordExpireService.createExpireToken(userId));
        }

        var permSet = permissionCacheService.loadPermissions(userId);
        var jti = UUID.randomUUID().toString();
        var accessToken = jwtService.generateAccessToken(userId, permSet.roleId(), permSet.codes(), jti);
        var rawRefresh = UUID.randomUUID().toString();

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(sha256(rawRefresh))
                .expiresAt(Instant.now().plusSeconds(refreshExpiry))
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .build());

        setAccessCookie(response, accessToken);
        setRefreshCookie(response, rawRefresh);
        sessionActivityService.register(jti, userId);
        securityEventRecorder.recordTokenIssued();
        // AU-2: logon event with session identifiers — required by FedRAMP AU-2 baseline
        auditService.log(userId, "USER_LOGGED_IN", "User", userId, null,
                Map.of("jti", jti,
                       "ipAddress", request.getRemoteAddr(),
                       "userAgent", Objects.requireNonNullElse(request.getHeader("User-Agent"), "unknown")),
                "success");
        return new AuthResponse(buildUserInfo(userId, permSet));
    }

    @Transactional
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        var rawToken = extractCookie(request, "refresh-token");
        if (rawToken == null) {
            throw new ForbiddenException("No refresh token provided");
        }

        var stored = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(sha256(rawToken))
                .orElseThrow(() -> new ForbiddenException("Invalid refresh token"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.setRevokedAt(Instant.now());
            refreshTokenRepository.save(stored);
            throw new ForbiddenException("Refresh token expired");
        }

        // SC-23: validate session authenticity — revoke and reject if IP changed since token was issued
        if (ipBindingEnabled) {
            var storedIp = stored.getIpAddress();
            if (storedIp != null) {
                if (!storedIp.equals(request.getRemoteAddr())) {
                    stored.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(stored);
                    auditService.log(stored.getUserId(), "SESSION_IP_MISMATCH", "User", stored.getUserId(), null,
                            Map.of("storedIp", storedIp, "requestIp", request.getRemoteAddr()), "failure");
                    securityEventRecorder.recordSessionIpMismatch();
                    throw new ForbiddenException("Session IP address changed");
                }
            }
        }

        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);

        var user = userRepository.findByIdAndDeletedAtIsNull(stored.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", stored.getUserId()));

        var permSet = permissionCacheService.loadPermissions(user.getId());
        var jti = UUID.randomUUID().toString();
        var newAccess = jwtService.generateAccessToken(user.getId(), permSet.roleId(), permSet.codes(), jti);
        var newRaw = UUID.randomUUID().toString();

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(newRaw))
                .expiresAt(Instant.now().plusSeconds(refreshExpiry))
                .build());

        setAccessCookie(response, newAccess);
        setRefreshCookie(response, newRaw);
        sessionActivityService.register(jti, user.getId());
        return new AuthResponse(buildUserInfo(user.getId(), permSet));
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        // Revoke access token immediately via denylist (AC-12) and deregister session (AC-10)
        var rawAccess = extractCookie(request, "access-token");
        if (rawAccess != null) {
            try {
                var claims = jwtService.validateAndParse(rawAccess);
                var jti = claims.getId();
                if (jti != null) {
                    var logoutUserId = UUID.fromString(claims.getSubject());
                    tokenDenylistService.deny(jti, claims.getExpiration().toInstant());
                    sessionActivityService.deregister(jti, logoutUserId);
                    // AU-2: logoff event — required by FedRAMP AU-2 baseline
                    auditService.log(logoutUserId, "USER_LOGGED_OUT", "User", logoutUserId, null,
                            Map.of("jti", jti), "success");
                }
            } catch (Exception ignored) {}
        }
        var rawRefresh = extractCookie(request, "refresh-token");
        if (rawRefresh != null) {
            refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(sha256(rawRefresh))
                    .ifPresent(t -> {
                        t.setRevokedAt(Instant.now());
                        refreshTokenRepository.save(t);
                    });
        }
        clearCookie(response, "access-token", "/api");
        clearCookie(response, "refresh-token", "/api/v1/auth");
    }

    @Transactional(readOnly = true)
    public UserInfo getMe(UUID userId) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        var permSet = permissionCacheService.loadPermissions(userId);
        return buildUserInfo(userId, permSet);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        var credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Credential not found"));

        if (!passwordEncoder.matches(request.currentPassword(), credential.getPasswordHash())) {
            throw new WrongPasswordException();
        }

        // IA-5(1)(b): enforce minimum password lifetime to prevent rapid cycling that defeats history control
        if (passwordMinAgeDays > 0) {
            var earliest = credential.getPasswordChangedAt().plusSeconds((long) passwordMinAgeDays * 86_400);
            if (Instant.now().isBefore(earliest)) {
                throw new PasswordPolicyException(List.of(
                        "Password cannot be changed yet — minimum age is " + passwordMinAgeDays + " day(s)"));
            }
        }

        var user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        passwordPolicyService.validate(request.newPassword(), user.getEmail(), user.getFullName());
        passwordHistoryService.checkNotReused(userId, request.newPassword());

        var encoded = passwordEncoder.encode(request.newPassword());
        credential.setPasswordHash(encoded);
        credential.setPasswordChangedAt(Instant.now());
        credentialRepository.save(credential);
        passwordHistoryService.record(userId, encoded);
        permissionCacheService.evict(userId);
        // AU-2: audit password changes so credential rotation events appear in the audit trail
        auditService.log(userId, "USER_PASSWORD_CHANGED", "User", userId, null, null, "success");
    }

    @Transactional
    public AuthResponse forceChangePassword(ForceChangePasswordRequest request,
                                             HttpServletRequest httpRequest,
                                             HttpServletResponse httpResponse) {
        var userId = passwordExpireService.validateAndConsume(request.expireToken());
        var credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Credential not found"));
        var user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        passwordPolicyService.validate(request.newPassword(), user.getEmail(), user.getFullName());
        passwordHistoryService.checkNotReused(userId, request.newPassword());

        var encoded = passwordEncoder.encode(request.newPassword());
        credential.setPasswordHash(encoded);
        credential.setPasswordChangedAt(Instant.now());
        credential.setMustChangePassword(false);
        credentialRepository.save(credential);
        passwordHistoryService.record(userId, encoded);
        permissionCacheService.evict(userId);
        auditService.log(userId, "USER_PASSWORD_FORCE_CHANGED", "User", userId, null, null, "success");

        return issueTokensForUser(userId, httpRequest, httpResponse);
    }

    private boolean isPasswordExpired(Credential credential) {
        if (passwordMaxAgeDays <= 0) return false;
        var expiry = credential.getPasswordChangedAt().plusSeconds((long) passwordMaxAgeDays * 86_400);
        return Instant.now().isAfter(expiry);
    }

    private void handleFailedLogin(Credential credential) {
        credential.setFailedAttempts(credential.getFailedAttempts() + 1);
        if (credential.getFailedAttempts() >= 5) {
            credential.setLockedUntil(Instant.now().plusSeconds(900));
            securityEventRecorder.recordAccountLockout();
        }
        credentialRepository.save(credential);
    }

    private UserInfo buildUserInfo(UUID userId, PermissionCacheService.PermissionSet permSet) {
        var user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        var roleName = permSet.roleId() != null
                ? roleRepository.findById(permSet.roleId()).map(r -> r.getName()).orElse(null)
                : null;
        var previousLoginAt = credentialRepository.findByUserId(userId)
                .map(c -> c.getPreviousLoginAt())
                .orElse(null);
        return new UserInfo(user.getId(), user.getFullName(), user.getEmail(),
                permSet.roleId(), roleName, permSet.codes(), previousLoginAt);
    }

    private void setAccessCookie(HttpServletResponse response, String token) {
        var c = new Cookie("access-token", token);
        c.setHttpOnly(true);
        c.setSecure(cookieSecure);
        c.setPath("/api");
        c.setMaxAge(accessExpiry);
        c.setAttribute("SameSite", "Strict");
        response.addCookie(c);
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        var c = new Cookie("refresh-token", token);
        c.setHttpOnly(true);
        c.setSecure(cookieSecure);
        c.setPath("/api/v1/auth");
        c.setMaxAge(refreshExpiry);
        c.setAttribute("SameSite", "Strict");
        response.addCookie(c);
    }

    private void clearCookie(HttpServletResponse response, String name, String path) {
        var c = new Cookie(name, "");
        c.setHttpOnly(true);
        c.setSecure(cookieSecure);
        c.setPath(path);
        c.setMaxAge(0);
        response.addCookie(c);
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst().orElse(null);
    }

    String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
