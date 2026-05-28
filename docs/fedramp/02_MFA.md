# 02 — Multi-Factor Authentication (MFA)
> Controls: IA-2(1) (BLOCKER), IA-2(2) (BLOCKER), AC-8 (Gap)
> Priority: P0-2, P1-6
> Effort: L — 4–6 weeks

---

## Problem Statement

No user, including admins, is required to present a second factor. FedRAMP Moderate mandates MFA for **all** users (IA-2(2)) and specifically for **privileged users** (IA-2(1)). Both are hard blockers for ATO.

Additionally, AC-8 requires a system-use notification banner to appear **before** authentication — there is currently none.

---

## 1. MFA Strategy

| Factor | Mechanism | Required For |
|--------|-----------|--------------|
| Primary | Password (existing) | All users |
| Second (soft token) | TOTP — RFC 6238 (Google Authenticator, Authy) | All users (IA-2(2)) |
| Second (phishing-resistant) | WebAuthn / FIDO2 hardware key | Privileged users (IA-2(1) Enhancement) |
| PIV/CAC | SAML federation via login.gov | Federal employees (IA-2(12)) → see [03_PIV_SAML.md](03_PIV_SAML.md) |

TOTP satisfies IA-2(1) and IA-2(2) baseline. WebAuthn is recommended for admins as a phishing-resistant option and satisfies IA-2(1) enhanced requirements.

---

## 2. Database Schema

### New columns on `users` table

```sql
-- Flyway migration: V21__add_mfa_fields.sql
ALTER TABLE users
    ADD COLUMN mfa_enabled        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN mfa_secret         TEXT,                          -- TOTP secret, AES-256 encrypted at rest
    ADD COLUMN mfa_method         VARCHAR(20)          DEFAULT 'TOTP',  -- TOTP | WEBAUTHN
    ADD COLUMN mfa_enrolled_at    TIMESTAMPTZ,
    ADD COLUMN mfa_backup_codes   TEXT[];               -- hashed backup codes

-- WebAuthn credentials (one user → many passkeys)
CREATE TABLE webauthn_credentials (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    credential_id       BYTEA       NOT NULL UNIQUE,
    public_key_cose     BYTEA       NOT NULL,
    sign_count          BIGINT      NOT NULL DEFAULT 0,
    aaguid              UUID,
    display_name        VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at        TIMESTAMPTZ
);
CREATE INDEX idx_webauthn_user ON webauthn_credentials(user_id);
```

### MFA session tracking

```sql
-- Flyway migration: V22__mfa_pending_sessions.sql
CREATE TABLE mfa_pending_sessions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    challenge_token TEXT        NOT NULL UNIQUE,    -- short-lived token after password OK
    expires_at      TIMESTAMPTZ NOT NULL,
    ip_address      INET,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mfa_pending_user ON mfa_pending_sessions(user_id);
```

---

## 3. Authentication Flow

```
POST /api/v1/auth/login
  ├── Validate password
  ├── If mfa_enabled = FALSE → issue tokens (existing flow, still works during roll-out)
  └── If mfa_enabled = TRUE  → return HTTP 202 { "mfaRequired": true, "challengeToken": "<uuid>" }

POST /api/v1/auth/mfa/verify
  ├── Body: { "challengeToken": "<uuid>", "code": "123456" }
  ├── Look up mfa_pending_sessions by challengeToken (must be < 5 min old)
  ├── Validate TOTP code against mfa_secret
  └── Issue access + refresh tokens (same as current login success path)
```

---

## 4. Backend Implementation

### Dependencies (`pom.xml`)

```xml
<!-- TOTP -->
<dependency>
    <groupId>dev.samstevens.totp</groupId>
    <artifactId>totp-spring-boot-starter</artifactId>
    <version>1.7.1</version>
</dependency>

<!-- WebAuthn (Yubico java-webauthn-server) -->
<dependency>
    <groupId>com.yubico</groupId>
    <artifactId>webauthn-server-core</artifactId>
    <version>2.5.3</version>
</dependency>
```

### `MfaService.java`

```java
package com.demo.app.iam.service;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MfaService {

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final CodeGenerator  codeGenerator   = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
    private final CodeVerifier   codeVerifier    = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public String buildOtpAuthUri(String secret, String email) {
        // Returns otpauth:// URI for QR code generation
        return "otpauth://totp/HRPortal:" + email
               + "?secret=" + secret
               + "&issuer=HRPortal"
               + "&algorithm=SHA1"
               + "&digits=6"
               + "&period=30";
    }

    public boolean verifyCode(String secret, String code) {
        return codeVerifier.isValidCode(secret, code);
    }
}
```

### New API endpoints

```
POST /api/v1/auth/mfa/setup        → generate TOTP secret, return QR code URI
POST /api/v1/auth/mfa/setup/verify → confirm first TOTP code, enable MFA on account
POST /api/v1/auth/mfa/verify       → verify code against pending challenge (login step 2)
DELETE /api/v1/auth/mfa            → disable MFA (requires current password + TOTP code)
GET  /api/v1/auth/mfa/backup-codes → regenerate backup codes
```

### Backup codes

Generate 10 × 8-character alphanumeric codes on enrollment. Store as SHA-256 hashes in `users.mfa_backup_codes` array. Each code is single-use; remove from array on use.

```java
public List<String> generateBackupCodes() {
    return IntStream.range(0, 10)
        .mapToObj(i -> RandomStringUtils.randomAlphanumeric(8).toUpperCase())
        .toList();
}
```

### `AuthService.java` — modify `login()`

```java
public AuthResponse login(LoginRequest req) {
    User user = userRepository.findByEmail(req.email())
        .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

    if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
        throw new BadCredentialsException("Invalid credentials");
    }

    if (user.isMfaEnabled()) {
        // Create pending challenge
        MfaPendingSession challenge = MfaPendingSession.builder()
            .userId(user.getId())
            .challengeToken(UUID.randomUUID().toString())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
        mfaPendingSessionRepository.save(challenge);
        return AuthResponse.mfaRequired(challenge.getChallengeToken());
    }

    return issueTokens(user);
}
```

---

## 5. Angular Frontend Implementation

### New components/routes needed

```
src/app/auth/
  ├── pages/
  │   ├── mfa-setup/          → QR code display + verify first code
  │   ├── mfa-verify/         → Enter TOTP code after password login
  │   └── mfa-backup/         → Show/copy backup codes
  └── services/
      └── mfa.service.ts      → HTTP calls to /api/v1/auth/mfa/*
```

### `mfa-verify.component.ts` (skeleton)

```typescript
@Component({
  selector: 'app-mfa-verify',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="mfa-container">
      <h2>Two-Factor Authentication</h2>
      <p>Enter the 6-digit code from your authenticator app.</p>
      <form [formGroup]="form" (ngSubmit)="verify()">
        <input formControlName="code" maxlength="6" inputmode="numeric"
               autocomplete="one-time-code" placeholder="000000">
        <button type="submit" [disabled]="form.invalid || loading()">Verify</button>
      </form>
      <a routerLink="/auth/mfa-backup">Use a backup code</a>
    </div>
  `
})
export class MfaVerifyComponent {
  protected readonly form    = new FormGroup({ code: new FormControl('', [Validators.required, Validators.pattern(/^\d{6}$/)]) });
  protected readonly loading = signal(false);

  private readonly challengeToken = inject(ActivatedRoute).snapshot.queryParams['token'];
  private readonly mfaService     = inject(MfaService);
  private readonly router         = inject(Router);

  verify(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.mfaService.verify({ challengeToken: this.challengeToken, code: this.form.value.code! })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: () => this.router.navigate(['/dashboard']),
        error: () => this.form.setErrors({ invalidCode: true }),
      });
  }
}
```

### Auth guard update (`auth.guard.ts`)

```typescript
// Redirect to MFA setup if user is authenticated but MFA not enrolled
export const mfaRequiredGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated() && !auth.user()?.mfaEnabled) {
    return router.createUrlTree(['/auth/mfa-setup']);
  }
  return true;
};
```

Apply `mfaRequiredGuard` to all protected routes. After a grace period (30 days), enforce MFA enrollment.

---

## 6. Login Banner (AC-8)

FedRAMP requires a system-use notification displayed **before** the user authenticates.

### Backend — `/api/v1/auth/banner` (public endpoint)

Add to `SecurityConfig.java`:
```java
.requestMatchers("/api/v1/auth/banner").permitAll()
```

```java
@GetMapping("/banner")
public BannerResponse getBanner() {
    return new BannerResponse("""
        NOTICE: This is a U.S. Government information system. \
        By using this system, you consent to monitoring and recording. \
        Unauthorized use is prohibited and subject to criminal penalties. \
        Use of this system implies agreement with applicable policies.
        """);
}
```

### Angular — login page update

```html
<!-- login.html — add above the form -->
<div class="alert alert-warning border-warning mb-4" role="alert">
  <strong>System Use Notification</strong>
  <p class="mb-0 small mt-1">{{ banner() }}</p>
  <div class="form-check mt-2">
    <input type="checkbox" class="form-check-input" id="bannerAck" [(ngModel)]="bannerAcknowledged">
    <label class="form-check-label small" for="bannerAck">
      I have read and acknowledge this notice.
    </label>
  </div>
</div>
```

Disable the login button until `bannerAcknowledged = true`.

---

## 7. MFA Enforcement Schedule

| Phase | Scope | Date |
|-------|-------|------|
| Opt-in available | All users | Deploy + 0 days |
| **Required for ADMIN/SUPER_ADMIN** | Privileged users (IA-2(1)) | Deploy + 14 days |
| Required for all users | All users (IA-2(2)) | Deploy + 60 days |
| Block login without MFA | Hard enforcement | Deploy + 90 days |

---

## 8. Acceptance Criteria

- [ ] `POST /api/v1/auth/login` returns `{ mfaRequired: true, challengeToken: "..." }` when MFA is enabled
- [ ] `POST /api/v1/auth/mfa/verify` with invalid code returns HTTP 401
- [ ] `POST /api/v1/auth/mfa/verify` with expired challenge token returns HTTP 401
- [ ] TOTP setup page displays scannable QR code
- [ ] Backup codes are single-use (second use returns 401)
- [ ] Login page shows system-use banner before any credential field
- [ ] Banner must be acknowledged (checkbox checked) before login button is enabled
- [ ] ADMIN role cannot log in without MFA after enforcement date
- [ ] Audit events generated for: MFA enrollment, MFA verify success, MFA verify failure, MFA disabled

---

## 9. Related Documents

- [01_FIPS_CRYPTO.md](01_FIPS_CRYPTO.md) — TOTP uses HMAC-SHA1; ensure BC-FIPS provides it
- [03_PIV_SAML.md](03_PIV_SAML.md) — PIV/CAC is the preferred second factor for federal employees
- [05_AUDIT_SIEM.md](05_AUDIT_SIEM.md) — All MFA events must be audited per AU-2
- [07_SESSION_HARDENING.md](07_SESSION_HARDENING.md) — MFA challenge tokens need short TTL + cleanup
