package com.demo.app.iam.controller;

import com.demo.app.iam.dto.AuthResponse;
import com.demo.app.iam.dto.UserInfo;
import com.demo.app.iam.service.AuthService;
import com.demo.app.iam.service.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;
    private final AuthService authService;

    // Step 1 of MFA enrollment (authenticated user — must already have a JWT)
    @PostMapping("/setup")
    public ResponseEntity<MfaService.MfaSetupResponse> setup(
            @AuthenticationPrincipal String userId,
            @RequestParam String email) {
        return ResponseEntity.ok(mfaService.initSetup(UUID.fromString(userId), email));
    }

    // Step 2 of MFA enrollment (authenticated user)
    @PostMapping("/setup/verify")
    public ResponseEntity<Map<String, List<String>>> setupVerify(
            @AuthenticationPrincipal String userId,
            @RequestBody SetupVerifyRequest req) {
        var backupCodes = mfaService.confirmSetup(UUID.fromString(userId), req.code());
        return ResponseEntity.ok(Map.of("backupCodes", backupCodes));
    }

    // --- Enrollment from login flow (unauthenticated, protected by enrollment token) ---

    // Phase 1: resolve enrollment token → generate TOTP secret + QR code
    @PostMapping("/enroll/init")
    public ResponseEntity<MfaService.MfaSetupResponse> enrollInit(
            @RequestBody EnrollInitRequest req) {
        return ResponseEntity.ok(mfaService.initSetupByToken(req.enrollmentToken()));
    }

    // Phase 2: verify first TOTP code, enable MFA, and issue session tokens
    @PostMapping("/enroll/confirm")
    public ResponseEntity<EnrollCompleteResponse> enrollConfirm(
            @RequestBody EnrollConfirmRequest req,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        var result = mfaService.confirmSetupByToken(req.enrollmentToken(), req.totpCode());
        var authResponse = authService.issueTokensForUser(result.userId(), httpRequest, httpResponse);
        return ResponseEntity.ok(new EnrollCompleteResponse(authResponse.user(), result.backupCodes()));
    }

    // --- Challenge verify (called after password-OK login when mfaRequired=true) ---

    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verify(
            @RequestBody MfaVerifyRequest req,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        var userId = mfaService.verifyChallenge(req.challengeToken(), req.code());
        return ResponseEntity.ok(authService.issueTokensForUser(userId, httpRequest, httpResponse));
    }

    // Disable MFA (requires current TOTP code)
    @DeleteMapping
    public ResponseEntity<Void> disable(
            @AuthenticationPrincipal String userId,
            @RequestBody DisableMfaRequest req) {
        mfaService.disable(UUID.fromString(userId), req.code());
        return ResponseEntity.noContent().build();
    }

    public record SetupVerifyRequest(
            @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

    public record MfaVerifyRequest(
            @NotBlank String challengeToken,
            @NotBlank String code) {}

    public record DisableMfaRequest(
            @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

    public record EnrollInitRequest(@NotBlank String enrollmentToken) {}

    public record EnrollConfirmRequest(
            @NotBlank String enrollmentToken,
            @NotBlank @Pattern(regexp = "\\d{6}") String totpCode) {}

    public record EnrollCompleteResponse(UserInfo user, List<String> backupCodes) {}
}
