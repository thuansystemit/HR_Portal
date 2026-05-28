# FedRAMP Moderate — Implementation Overview
> Baseline: NIST SP 800-53 Rev 5 · FedRAMP Moderate (325 controls)
> System category: Moderate-impact (FIPS 199) — processes candidate PII and employment records
> Last updated: 2026-05-28

---

## Document Index

| Doc | Topic | Priority | Code Status |
|-----|-------|----------|-------------|
| [01_FIPS_CRYPTO.md](01_FIPS_CRYPTO.md) | FIPS 140-2 cryptography (BC-FIPS, PBKDF2, KMS) | P0 | ⚠️ In Progress |
| [02_MFA.md](02_MFA.md) | Multi-factor authentication (TOTP + WebAuthn) | P0 | ⚠️ In Progress |
| [03_PIV_SAML.md](03_PIV_SAML.md) | PIV/CAC smart card + SAML federation (login.gov) | P0 | ❌ Not Started |
| [04_GOVCLOUD_MIGRATION.md](04_GOVCLOUD_MIGRATION.md) | AWS GovCloud migration | P0 | ❌ Not Started |
| [05_AUDIT_SIEM.md](05_AUDIT_SIEM.md) | Audit log hardening + SIEM integration | P1 | ⚠️ In Progress |
| [06_SECURITY_SCANNING.md](06_SECURITY_SCANNING.md) | CI security scanning (SAST/DAST/SCA/malware) | P1 | ⚠️ In Progress |
| [07_SESSION_HARDENING.md](07_SESSION_HARDENING.md) | Server-side session management | P1 | ⚠️ In Progress |
| [08_DATA_PROTECTION.md](08_DATA_PROTECTION.md) | CUI/PII field-level encryption + retention | P1/P2 | ⚠️ In Progress |
| [09_NETWORK_VPC.md](09_NETWORK_VPC.md) | GovCloud VPC architecture + GuardDuty + WAF | P1/P2 | ❌ Not Started |
| [10_CONMON.md](10_CONMON.md) | Continuous monitoring program | P2 | ❌ Not Started |

---

## Control Family Gap Matrix

> **Legend:** ✅ Done · ⚠️ Partial · ❌ Gap · 🔨 Implemented (pending deployment/enforcement)

### AC — Access Control

| Control | Status | Remaining Gap | Doc |
|---------|--------|---------------|-----|
| AC-1 Policy & Procedures | ❌ Gap | No documented policy | [10_CONMON](10_CONMON.md) |
| AC-2 Account Management | ⚠️ Partial | No automated account review or disable-on-termination | [07_SESSION](07_SESSION_HARDENING.md) |
| AC-3 Access Enforcement | ✅ | `@PreAuthorize` on all controllers | — |
| AC-4 Info Flow Enforcement | 🔨 Partial | `ui-avatars.com` egress **removed**; VPC-level flow controls pending GovCloud | [09_NETWORK](09_NETWORK_VPC.md) |
| AC-6 Least Privilege | ✅ | `PERM_*` granular model; HR has zero IAM perms | — |
| AC-7 Unsuccessful Logins | ✅ | Account lockout after 5 failures (15 min) | — |
| AC-8 System Use Notification | 🔨 **Done** | Banner endpoint + Angular acknowledgement **implemented** | [02_MFA](02_MFA.md) |
| AC-10 Concurrent Session Control | 🔨 **Done** | `user:sessions:<userId>` Redis set tracks active jtis; `countActiveSessions()` prunes expired entries; cap enforced at `issueTokensForUser()` | [07_SESSION](07_SESSION_HARDENING.md) |
| AC-11 Session Lock | 🔨 **Done** | Server-side 30-min idle timeout via Redis `jti` key **implemented** | [07_SESSION](07_SESSION_HARDENING.md) |
| AC-12 Session Termination | 🔨 **Done** | Redis JWT denylist on logout **implemented**; `jti` in all tokens | [07_SESSION](07_SESSION_HARDENING.md) |
| AC-17 Remote Access | ⚠️ Partial | TLS only; no VPN/jump box | [09_NETWORK](09_NETWORK_VPC.md) |

### AU — Audit and Accountability

| Control | Status | Remaining Gap | Doc |
|---------|--------|---------------|-----|
| AU-2 Audit Events | ⚠️ Partial | `correlationId`/`sessionId` added; recruitment pipeline audit coverage still incomplete | [05_AUDIT](05_AUDIT_SIEM.md) |
| AU-3 Content of Audit Records | 🔨 **Done** | `correlation_id` (from MDC `requestId`) and `session_id` (JWT `jti`) **added** to all events | [05_AUDIT](05_AUDIT_SIEM.md) |
| AU-5 Response to Audit Failures | 🔨 **Done** | `@Async` **removed**; `AuditService.log()` is synchronous with `REQUIRES_NEW` — failures propagate | [05_AUDIT](05_AUDIT_SIEM.md) |
| AU-6 Audit Review & Analysis | ❌ Gap | No SIEM; manual only | [05_AUDIT](05_AUDIT_SIEM.md) |
| AU-9 Protection of Audit Info | ⚠️ Partial | No WORM/S3 Object Lock; app DB user can still DELETE | [05_AUDIT](05_AUDIT_SIEM.md) |
| AU-11 Audit Retention | ⚠️ Partial | 1 year in DB; S3 Object Lock 3-year archive not yet configured | [05_AUDIT](05_AUDIT_SIEM.md) |

### IA — Identification and Authentication

| Control | Status | Remaining Gap | Doc |
|---------|--------|---------------|-----|
| IA-2(1) MFA — Privileged | 🔨 **Done** | TOTP backend ✅ · MFA verify page ✅ · **Hard-block on login: unenrolled users get enrollment token** · `/mfa-setup` Angular page | [02_MFA](02_MFA.md) |
| IA-2(2) MFA — Non-Privileged | 🔨 **Done** | Same enforcement applies to all users; backup codes + disable flow complete | [02_MFA](02_MFA.md) |
| IA-2(12) PIV Credentials | ❌ **BLOCKER** | No PIV/CAC support; SAML federation not started | [03_PIV_SAML](03_PIV_SAML.md) |
| IA-3 Device Identification | 🔨 **Done** | Internal API key no longer accepts blank default — fail-secure | [07_SESSION](07_SESSION_HARDENING.md) |
| IA-5(1) Password Auth | 🔨 **Done** | `DelegatingPasswordEncoder` with PBKDF2-HMAC-SHA256 **implemented**; BCrypt migration via `{bcrypt}` prefix prefix on V24 migration | [01_FIPS](01_FIPS_CRYPTO.md) |
| IA-6 Authenticator Feedback | ✅ | Generic "Invalid credentials" message | — |
| IA-8(2) External Authenticators | ❌ Gap | No OIDC/SAML federation | [03_PIV_SAML](03_PIV_SAML.md) |

### SC — System and Communications Protection

| Control | Status | Remaining Gap | Doc |
|---------|--------|---------------|-----|
| SC-5 DoS Protection | ⚠️ Partial | Nginx rate limit only; no WAF/Shield | [09_NETWORK](09_NETWORK_VPC.md) |
| SC-7 Boundary Protection | 🔨 Partial | `CspFilter` **implemented** — removes `unsafe-inline` from `script-src`; adds `X-Frame-Options: DENY`, `X-Content-Type-Options`; VPC segmentation pending GovCloud | [09_NETWORK](09_NETWORK_VPC.md) |
| SC-8 Transmission Integrity | ✅ | TLS 1.3, HSTS, Secure cookies | — |
| SC-12 Crypto Key Management | ⚠️ **Partial** | JWT key now loads from PKCS12 keystore (persistent, not ephemeral); full KMS migration pending GovCloud | [01_FIPS](01_FIPS_CRYPTO.md) |
| SC-13 Cryptographic Protection | ⚠️ **Partial** | BC-FIPS provider registered; Azul Zulu JRE (FIPS-capable); PBKDF2 done; KMS for JWT pending | [01_FIPS](01_FIPS_CRYPTO.md) |
| SC-28 Protection at Rest | ⚠️ Partial | AWS KMS storage encryption; no field-level PII encryption yet | [08_DATA](08_DATA_PROTECTION.md) |

### SI — System and Information Integrity

| Control | Status | Remaining Gap | Doc |
|---------|--------|---------------|-----|
| SI-2 Flaw Remediation | 🔨 **Done** | GitHub Actions: OWASP Dependency-Check (SCA), Semgrep (SAST), Trivy (container), npm audit **implemented** | [06_SCANNING](06_SECURITY_SCANNING.md) |
| SI-3 Malicious Code Protection | ❌ Gap | No malware scan on CV uploads; GuardDuty Malware Protection not configured | [06_SCANNING](06_SECURITY_SCANNING.md) |
| SI-4 System Monitoring | ⚠️ Partial | Ops metrics only; no security events / SIEM | [05_AUDIT](05_AUDIT_SIEM.md) |
| SI-10 Input Validation | ✅ | Jakarta Bean Validation; JPA parameterized | — |

### Other Families (all ❌ Gap — documentation/process required)

| Family | Required Documents |
|--------|--------------------|
| CP — Contingency Planning | Contingency Plan, DR runbook (see [10_CONMON](10_CONMON.md)) |
| CM — Configuration Management | CM Plan, CIS baselines, component inventory |
| IR — Incident Response | IRP, US-CERT reporting procedures |
| RA — Risk Assessment | FIPS 199 categorization, formal risk assessment |
| CA — Authorization & Monitoring | SSP, POA&M, ConMon plan |
| PS — Personnel Security | Screening, termination, transfer procedures |
| PE — Physical/Environmental | Inherited from AWS GovCloud (must be documented in SSP) |

---

## Priority Summary

### P0 — Absolute Blockers (cannot obtain ATO without these)

| ID | Issue | Status | Remaining Work | Owner Doc |
|----|-------|--------|----------------|-----------|
| P0-1 | No FIPS 140-2 validated cryptography (SC-13) | ⚠️ In Progress | BC-FIPS ✅ · PBKDF2 ✅ · Azul Zulu ✅ · **KMS JWT pending GovCloud** | [01_FIPS](01_FIPS_CRYPTO.md) |
| P0-2 | No MFA for any user (IA-2(1), IA-2(2)) | 🔨 **Done** | TOTP backend ✅ · MFA pages ✅ · **Enrollment hard-block ✅** · `/mfa-setup` page ✅ | [02_MFA](02_MFA.md) |
| P0-3 | No PIV/CAC smart card support (IA-2(12)) | ❌ Not Started | Spring Security SAML2 · login.gov registration | [03_PIV_SAML](03_PIV_SAML.md) |
| P0-4 | No System Security Plan (CA-6) | ❌ Not Started | FedRAMP SSP template — 8–12 wks | SSP (separate) |
| P0-5 | Running on commercial AWS, not GovCloud | ❌ Not Started | Terraform migration · data migration · FIPS endpoints | [04_GOVCLOUD](04_GOVCLOUD_MIGRATION.md) |

### P1 — Required Before ATO

| ID | Issue | Status | Remaining Work | Owner Doc |
|----|-------|--------|----------------|-----------|
| P1-1 | JWT key in-memory (SC-12) | ⚠️ In Progress | Keystore-based ✅ · **KMS migration pending GovCloud** | [01_FIPS](01_FIPS_CRYPTO.md) |
| P1-2 | Audit incomplete + lossy + 1-yr retention | ⚠️ In Progress | Sync+REQUIRES_NEW ✅ · correlationId/sessionId ✅ · **SIEM · WORM · 3-yr retention pending** | [05_AUDIT](05_AUDIT_SIEM.md) |
| P1-3 | No security scanning in CI (RA-5, SI-2) | ✅ **Done** | SCA · SAST · Trivy · npm audit · ZAP in `security.yml` | [06_SCANNING](06_SECURITY_SCANNING.md) |
| P1-4 | No malware scan on uploads (SI-3) | ❌ Not Started | GuardDuty Malware Protection or ClamAV Lambda | [06_SCANNING](06_SECURITY_SCANNING.md) |
| P1-5 | Client-side only idle timeout; no session limits | ✅ **Done** | Idle timeout ✅ · denylist ✅ · concurrent session count limit ✅ (Redis Set per user, cap=5, configurable) | [07_SESSION](07_SESSION_HARDENING.md) |
| P1-6 | No login banner (AC-8) | ✅ **Done** | `/api/v1/auth/banner` endpoint + Angular acknowledgement checkbox | [02_MFA](02_MFA.md) |
| P1-7 | `ui-avatars.com` PII egress (AC-4) | ✅ **Done** | Replaced with inline SVG avatar using local initials | [08_DATA](08_DATA_PROTECTION.md) |
| P1-8 | `unsafe-inline` in CSP (SC-7) | 🔨 **Done** | `CspFilter.java` sets per-request nonce; `script-src 'self' 'nonce-{n}'` — no more `unsafe-inline` for scripts | [09_NETWORK](09_NETWORK_VPC.md) |
| P1-9 | Static internal API key with hardcoded default (IA-3) | ✅ **Done** | Fails secure when `INTERNAL_API_KEY` unset; logs warning | [07_SESSION](07_SESSION_HARDENING.md) |

### P2 — Required Within 90 Days of ATO

| ID | Issue | Status | Owner Doc |
|----|-------|--------|-----------|
| P2-1 | No ConMon program (CA-7) | ❌ Not Started | [10_CONMON](10_CONMON.md) |
| P2-2 | No Incident Response Plan (IR-4,6,8) | ❌ Not Started | [10_CONMON](10_CONMON.md) |
| P2-3 | No Contingency Plan (CP-2,10) | ❌ Not Started | [10_CONMON](10_CONMON.md) |
| P2-4 | No CM Plan / CIS baselines (CM-1,2) | ❌ Not Started | [10_CONMON](10_CONMON.md) |
| P2-5 | No personnel termination procedures (PS-4,5) | ❌ Not Started | [10_CONMON](10_CONMON.md) |
| P2-6 | No field-level encryption on `cv_candidates` PII (SC-28) | ❌ Not Started | [08_DATA](08_DATA_PROTECTION.md) |
| P2-7 | External LLM processes candidate PII (SA-9) | ❌ Not Started | [08_DATA](08_DATA_PROTECTION.md) |
| P2-8 | No audit/report export (CA-7) | ❌ Not Started | [05_AUDIT](05_AUDIT_SIEM.md) |

---

## Implementation Progress Scoreboard

| Layer | Done | In Progress | Not Started | Total |
|-------|:----:|:-----------:|:-----------:|:-----:|
| P0 — Absolute Blockers | 1 | 1 | 3 | 5 |
| P1 — Required Before ATO | 7 | 1 | 1 | 9 |
| P2 — 90-Day Post-ATO | 0 | 0 | 8 | 8 |
| **All priorities** | **8** | **2** | **12** | **22** |

**Controls resolved or materially improved (sprint 1):** AC-8, AC-11, AC-12, AU-3, AU-5, IA-3, IA-5(1), SI-2, P1-6, P1-7, P1-9  
**Controls resolved or materially improved (sprint 2):** IA-2(1), IA-2(2), AC-10, SC-7 (script CSP)

---

## What Was Implemented (2026-05-28 Sprint)

| Item | Files Changed | Controls |
|------|--------------|---------|
| Azul Zulu JRE base image | `Dockerfile` | SC-13 |
| BC-FIPS provider registration | `FipsConfig.java`, `pom.xml` | SC-13 |
| PBKDF2-HMAC-SHA256 + DelegatingPasswordEncoder (BCrypt migration) | `SecurityConfig.java`, `V24__fedramp_crypto.sql`, `Credential.java` | IA-5(1), SC-13 |
| JWT keystore-based key (persistent, not in-memory) | `JwtConfig.java`, `application.yml` | SC-12 |
| JWT denylist + immediate logout revocation | `TokenDenylistService.java`, `JwtCookieAuthFilter.java`, `AuthService.java` | AC-12 |
| Server-side 30-min idle timeout | `SessionActivityService.java`, `JwtCookieAuthFilter.java` | AC-11 |
| `jti` claim on all JWT tokens | `JwtService.java` | AC-12 |
| Audit `@Async` removed — synchronous with REQUIRES_NEW | `AuditService.java` | AU-5 |
| `correlation_id` + `session_id` in audit events | `AuditEvent.java`, `V25__fedramp_audit.sql` | AU-3 |
| MFA tables + TOTP backend (MfaService, MfaController) | `V26__fedramp_mfa.sql`, `MfaService.java`, `MfaController.java` | IA-2(1), IA-2(2) |
| MFA two-step login flow (challenge token) | `AuthService.java`, `AuthResponse.java` | IA-2(1), IA-2(2) |
| Angular MFA verify page + backup code mode | `mfa-verify.ts`, `mfa-verify.html`, `app.routes.ts` | IA-2(1), IA-2(2) |
| Login system-use banner (AC-8) | `AuthController.java`, `login.ts`, `login.html` | AC-8 |
| Removed `ui-avatars.com` — inline SVG initials avatar | `header.ts`, `header.html` | AC-4 |
| Internal API key fail-secure | `InternalApiKeyFilter.java` | IA-3 |
| CI scanning: SCA + SAST + Trivy + ZAP | `.github/workflows/security.yml`, `owasp-suppressions.xml` | SI-2, RA-5 |
| TOTP library dependency | `pom.xml` | IA-2 |

### Sprint 2 — 2026-05-28 (this session)

| Item | Files Changed | Controls |
|------|--------------|---------|
| MFA enrollment hard-block: unenrolled users get enrollment token on login | `AuthService.java`, `AuthResponse.java`, `MfaService.java` | IA-2(1), IA-2(2) |
| Redis-backed enrollment session (`mfa:enroll:{token}`, 10-min TTL) | `MfaService.java` | IA-2 |
| Unauthenticated enrollment endpoints (`/mfa/enroll/init`, `/mfa/enroll/confirm`) | `MfaController.java` | IA-2 |
| Angular MFA setup page (QR scan → confirm TOTP → show backup codes) | `mfa-setup.ts`, `mfa-setup.html`, `app.routes.ts` | IA-2 |
| Concurrent session count limit (AC-10, default cap=5) | `SessionActivityService.java`, `AuthService.java`, `application.yml` | AC-10 |
| Per-user Redis session set `user:sessions:<userId>` with auto-pruning | `SessionActivityService.java` | AC-10 |
| Idle session deregistration from user session set | `JwtCookieAuthFilter.java`, `AuthService.java` | AC-10, AC-11 |
| `jti` pre-generated and passed into `JwtService` (enables session registration) | `JwtService.java`, `AuthService.java` | AC-12 |
| `CspFilter` — removes `unsafe-inline` from `script-src`; adds security headers | `CspFilter.java`, `SecurityConfig.java` | SC-7 |

---

## Next Implementation Targets

### Immediate (sprint 3 candidates)

| Item | Effort | Blocks |
|------|--------|--------|
| ~~MFA enrollment enforcement hard-block~~ | ~~S~~ | ✅ Done |
| ~~Concurrent session count limit~~ | ~~S~~ | ✅ Done |
| ~~CSP nonce (`CspFilter.java`)~~ | ~~M~~ | ✅ Done |
| GuardDuty Malware Protection for S3 CV uploads | M — 3 days | P1-4 / SI-3 |

### GovCloud Prerequisites (P0-5 must precede these)

| Item | Depends On | Controls |
|------|-----------|---------|
| AWS KMS JWT signing key | GovCloud account | SC-12 full |
| S3 Object Lock 3-year audit archive | GovCloud S3 | AU-9, AU-11 |
| SIEM (CloudWatch + EventBridge) | GovCloud CloudWatch | AU-6, SI-4 |
| Field-level PII encryption (JPA converter + KMS) | GovCloud KMS | SC-28, P2-6 |
| Self-hosted LLM (Bedrock or Ollama EC2) | GovCloud VPC | SA-9, P2-7 |

### PIV/SAML (P0-3 — 6–10 weeks, independent track)

| Item | Effort |
|------|--------|
| Spring Security SAML2 SP config | M |
| login.gov sandbox registration | S |
| `FederatedIdentity` table + JIT user provisioning | M |
| Angular "Sign in with PIV" button | S |

---

## Authorization Roadmap

```
Q3 2026  Wks 1-6    Phase 0: Foundation
                    ├── Engage 3PAO
                    ├── Begin SSP drafting (FedRAMP template)
                    ├── FIPS 199 security categorization
                    └── Draft 12 policy documents (AC/AU/IA/SC/SI/CP/CM/IR/RA/SA/CA/PS)

Q3-Q4    Wks 4-16   Phase 1: P0 Remediation
                    ├── P0-5: GovCloud provisioning + Terraform migration
                    ├── P0-1: KMS JWT + FIPS endpoints (BC-FIPS/PBKDF2 ✅ done)
                    ├── P0-2: MFA enforcement + WebAuthn (TOTP flow ✅ done)
                    └── P0-3: SAML + login.gov federation

Q4 2026  Wks 14-22  Phase 2: P1 Remediation
                    ├── P1-1: KMS-backed JWT signing (keystore interim ✅ done)
                    ├── P1-2: SIEM + WORM audit archive (sync/correlationId ✅ done)
                    ├── P1-3: CI scanning ✅ DONE
                    ├── P1-4: Malware scan on uploads
                    ├── P1-5: Concurrent session limit (idle timeout/denylist ✅ done)
                    ├── P1-6: Login banner ✅ DONE
                    ├── P1-7: ui-avatars.com removed ✅ DONE
                    ├── P1-8: CSP nonce (remove unsafe-inline)
                    ├── P1-9: Internal API key ✅ DONE
                    └── VPC hardening: GuardDuty, Config, WAF

Q1 2027  Wks 22-28  Phase 3: Assessment Prep
                    ├── 3PAO readiness assessment
                    ├── P2 items (ConMon, IRP, CP, CM policies)
                    └── Internal pen test + evidence collection

Q1-Q2    Wks 28-36  Phase 4: 3PAO Assessment
                    ├── SAP → assessment → pen test → SAR

Q2 2027  Wk 40      ATO GRANTED (target)

Ongoing             Phase 5: Continuous Monitoring
```

---

## Engineering Team Allocation

| Role | FTE | Primary Work |
|------|:---:|---|
| Security Engineer / Compliance | 2 | SSP, policies, POA&M, ConMon, 3PAO liaison |
| Backend Engineer | 2 | KMS JWT, SAML, SIEM, malware scan, field-level encryption |
| DevOps / Cloud Engineer | 1 | GovCloud migration, VPC, WAF, S3 Object Lock, GuardDuty |
| Full-Stack | 1 | MFA enforcement UI, CSP nonce, WebAuthn, PIV login button |

**Total elapsed time to ATO-readiness: 9–12 months**
