# FedRAMP Moderate — Implementation Overview
> Baseline: NIST SP 800-53 Rev 5 · FedRAMP Moderate (325 controls)
> System category: Moderate-impact (FIPS 199) — processes candidate PII and employment records
> Last updated: 2026-05-30 (sprint 34)

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
| AC-6(9) Log Use of Privileged Functions | 🔨 **Done** | `AuditController.export()` emits `AUDIT_EXPORT_DOWNLOADED` audit event (actor, from, to, action filter) after every successful download; error-path requests do not emit; `SecurityConfig` restricts `/actuator/loggers/**` to `PERM_analyticsView` (CM-7 bonus) | — |
| AC-2 Account Management | 🔨 **Done** | Session revocation on delete and deactivation — all active JWTs denied on termination | [07_SESSION](07_SESSION_HARDENING.md) |
| AC-3 Access Enforcement | ✅ | `@PreAuthorize` on all controllers | — |
| AC-4 Info Flow Enforcement | 🔨 Partial | `ui-avatars.com` egress **removed**; VPC-level flow controls pending GovCloud | [09_NETWORK](09_NETWORK_VPC.md) |
| AC-5 Separation of Duties | 🔨 **Done** | `UserService.update/delete/unlock` each throw `ForbiddenException("AC-5: ...")` when `actorId == targetId`; prevents any single IAM admin from unilaterally modifying their own role, status, or clearing their own lockout | — |
| AC-6 Least Privilege | ✅ | `PERM_*` granular model; HR has zero IAM perms | — |
| AC-7 Unsuccessful Logins | 🔨 **Done** | Password lockout after 5 failures (15 min); MFA challenge lockout after 5 failures (`app.mfa.max-failures`, 900 s, same policy); `POST /api/v1/users/{id}/unlock` admin endpoint; `MFA_LOCKOUT` audit event + `security.mfa.lockout` counter; `USER_ACCOUNT_UNLOCKED` + `security.auth.account.unlocked` | — |
| AC-8 System Use Notification | 🔨 **Done** | Banner endpoint + Angular acknowledgement **implemented** | [02_MFA](02_MFA.md) |
| AC-10 Concurrent Session Control | 🔨 **Done** | `user:sessions:<userId>` Redis set tracks active jtis; `countActiveSessions()` prunes expired entries; cap enforced at `issueTokensForUser()` | [07_SESSION](07_SESSION_HARDENING.md) |
| AC-11 Session Lock | 🔨 **Done** | Server-side 30-min idle timeout via Redis `jti` key **implemented** | [07_SESSION](07_SESSION_HARDENING.md) |
| AC-12 Session Termination | 🔨 **Done** | Redis JWT denylist on logout **implemented**; `jti` in all tokens | [07_SESSION](07_SESSION_HARDENING.md) |
| AC-9 Previous Logon Notification | 🔨 **Done** | `previous_login_at` stamped before `last_login_at` is overwritten on each login; surfaced in `UserInfo.previousLoginAt` via `buildUserInfo()` credential fetch | — |
| AC-17 Remote Access | ⚠️ Partial | TLS only; no VPN/jump box | [09_NETWORK](09_NETWORK_VPC.md) |

### AU — Audit and Accountability

| Control | Status | Remaining Gap | Doc |
|---------|--------|---------------|-----|
| AU-2 Audit Events | 🔨 **Done** | Full coverage: all IAM write operations (`USER_CREATED`, `USER_UPDATED`, `USER_DEACTIVATED`, `USER_DELETED`, `USER_ACCOUNT_UNLOCKED`, `USER_AUTO_DEACTIVATED`, `USER_PASSWORD_CHANGED`, `USER_PASSWORD_FORCE_CHANGED`, `USER_PASSWORD_ADMIN_RESET`, `USER_ROLE_CHANGED`, `AUDIT_EXPORT_DOWNLOADED`), MFA lifecycle (`MFA_ENROLLED`, `MFA_DISABLED`, `MFA_VERIFY_FAILED`, `MFA_BACKUP_CODE_USED`), CV, recruitment, hiring pipeline | [05_AUDIT](05_AUDIT_SIEM.md) |
| AU-3 Content of Audit Records | 🔨 **Done** | `correlation_id` (from MDC `requestId`) and `session_id` (JWT `jti`) **added** to all events | [05_AUDIT](05_AUDIT_SIEM.md) |
| AU-5 Response to Audit Failures | 🔨 **Done** | `@Async` **removed**; `AuditService.log()` is synchronous with `REQUIRES_NEW` — failures propagate | [05_AUDIT](05_AUDIT_SIEM.md) |
| AU-6 Audit Review & Analysis | 🔨 **Done** | `AuditService` emits structured JSON line to `AUDIT` logger after every DB save; `logback-spring.xml` routes `AUDIT` → rolling NDJSON file + Logstash TCP in prod; `GET /api/v1/admin/audit/events` paginated search endpoint (filters: from, to, action, actorId, entityType; page-size cap 200; ISO-8601 validation; `PERM_analyticsView` gate) implemented in `AuditQueryService` + `AuditController`; SIEM subscription still pending GovCloud | [05_AUDIT](05_AUDIT_SIEM.md) |
| AU-9 Protection of Audit Info | ⚠️ Improved | App layer: `@Immutable` entity, JPA listener, repository guards block all DELETE/UPDATE; DB trigger (V30) enforces at SQL level; S3 WORM/Object Lock still pending GovCloud | [05_AUDIT](05_AUDIT_SIEM.md) |
| AU-11 Audit Retention | ⚠️ Improved | Scheduled monthly archival to NDJSON.gz + bulk purge with maintenance-mode DB trigger bypass; S3 Object Lock 3-year WORM archive still pending GovCloud | [05_AUDIT](05_AUDIT_SIEM.md) |

### IA — Identification and Authentication

| Control | Status | Remaining Gap | Doc |
|---------|--------|---------------|-----|
| IA-2(1) MFA — Privileged | 🔨 **Done** | TOTP backend ✅ · MFA verify page ✅ · **Hard-block on login: unenrolled users get enrollment token** · `/mfa-setup` Angular page | [02_MFA](02_MFA.md) |
| IA-2(2) MFA — Non-Privileged | 🔨 **Done** | Same enforcement applies to all users; backup codes + disable flow complete | [02_MFA](02_MFA.md) |
| IA-2(8) Replay-Resistant Auth | 🔨 **Done** | Redis `mfa:used:{userId}:{code}` key (90 s TTL) blocks TOTP replay across `verifyChallenge`, `confirmSetup`, `disable`; `MFA_REPLAY_ATTEMPT` audit event + `security.mfa.replay.attempt` counter on blocked replay | — |
| IA-2(12) PIV Credentials | ❌ **BLOCKER** | No PIV/CAC support; SAML federation not started | [03_PIV_SAML](03_PIV_SAML.md) |
| IA-3 Device Identification | 🔨 **Done** | Internal API key no longer accepts blank default — fail-secure | [07_SESSION](07_SESSION_HARDENING.md) |
| IA-4(e) Inactive Account Deactivation | 🔨 **Done** | `InactiveAccountService` runs daily at 03:00 UTC; deactivates accounts inactive ≥ 90 days (configurable `ACCOUNT_INACTIVITY_DAYS`); `last_login_at` stamped on every successful credential validation; `USER_AUTO_DEACTIVATED` audit event + `security.user.auto.deactivated` counter; active sessions revoked | — |
| IA-5(1) Password Auth | 🔨 **Done** | `DelegatingPasswordEncoder` with PBKDF2-HMAC-SHA256 **implemented**; BCrypt migration via `{bcrypt}` prefix on V24 migration | [01_FIPS](01_FIPS_CRYPTO.md) |
| IA-5(1)(c) Admin Credential Reset | 🔨 **Done** | `POST /api/v1/users/{id}/reset-password` (`PERM_usersEdit`); AC-5 guard blocks self-reset; sets temp password + `mustChangePassword=true`; validates policy + history; revokes all active sessions; `USER_PASSWORD_ADMIN_RESET` audit event; 6 service tests + 1 controller test | — |
| IA-5(1)(b) Minimum Password Lifetime | 🔨 **Done** | `changePassword()` enforces minimum age: if `passwordChangedAt + minAgeDays > now()` throws `PasswordPolicyException("minimum age is N day(s)")`; `forceChangePassword()` (admin/expiry-forced) bypasses check; configurable `PASSWORD_MIN_AGE_DAYS:1`; disabled when `min-age-days=0`; 3 tests (too-recent, elapsed, disabled) | — |
| IA-5(1)(d) Password Expiry | 🔨 **Done** | `password_changed_at` column (V35); checked in `issueTokensForUser` after MFA — expired password blocks token issuance and returns `AuthResponse.passwordExpired(expireToken)`; 10-min Redis expire token (`pw:expire:{token}`) authorises `POST /api/v1/auth/force-change-password` which validates policy + history, stamps `passwordChangedAt`, and issues full session; configurable `PASSWORD_MAX_AGE_DAYS:60`; `USER_PASSWORD_FORCE_CHANGED` audit event | — |
| IA-5(1)(f) Force Change at First Use | 🔨 **Done** | `must_change_password` column (V37, default false); set `true` by `UserService.create()` for every admin-provisioned account; `issueTokensForUser` checks flag before date-based expiry — both funnel through same `AuthResponse.passwordExpired` + `forceChangePassword` flow; flag cleared to `false` after successful forced change; 2 new tests | — |
| IA-5(1)(h) Password History | 🔨 **Done** | NIST SP 800-63B 5.1.1.2: blocks reuse of last 5 passwords (configurable); enforced on `changePassword`; initial hash recorded on user creation; pruning keeps table bounded | [01_FIPS](01_FIPS_CRYPTO.md) |
| IA-5(3) Password Policy | 🔨 **Done** | NIST SP 800-63B: min 12 / max 128 chars; common-password blocklist; rejects passwords containing user email or name; structured 422 violations response | [01_FIPS](01_FIPS_CRYPTO.md) |
| IA-6 Authenticator Feedback | ✅ | Generic "Invalid credentials" message | — |
| IA-11 Re-authentication | 🔨 **Done** | Role change in `UserService.update()` calls `revokeAllSessions(id)` — all active JTIs are deny-listed immediately so the user must re-authenticate with new role; `USER_ROLE_CHANGED` audit event captures old and new roleId; prevents 15-min JWT window where demoted users retain elevated permissions | — |
| IA-8(2) External Authenticators | ❌ Gap | No OIDC/SAML federation | [03_PIV_SAML](03_PIV_SAML.md) |

### SC — System and Communications Protection

| Control | Status | Remaining Gap | Doc |
|---------|--------|---------------|-----|
| SC-5 DoS Protection | ⚠️ Improved | Redis-backed fixed-window per-IP rate limiter (shared across all pods); login/MFA 5 req/60 s, refresh 20 req/60 s; 429 + Retry-After (remaining window TTL); WAF/Shield still pending GovCloud | [09_NETWORK](09_NETWORK_VPC.md) |
| SC-7 Boundary Protection | 🔨 Partial | `CspFilter` sets 7 security headers: CSP nonce, `X-Frame-Options: DENY`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy` (camera/mic/geo/payment/usb blocked), `Cross-Origin-Opener-Policy: same-origin`; VPC segmentation pending GovCloud | [09_NETWORK](09_NETWORK_VPC.md) |
| SC-8 Transmission Integrity | ✅ | TLS 1.3, Secure cookies | — |
| SC-8(1) Transmission Confidentiality | 🔨 **Done** | `Strict-Transport-Security: max-age=31536000; includeSubDomains` on every response; `Cache-Control: no-store, max-age=0` on all `/api/**` responses to prevent browser caching of sensitive data | — |
| SC-12 Crypto Key Management | ⚠️ **Partial** | JWT key now loads from PKCS12 keystore (persistent, not ephemeral); full KMS migration pending GovCloud | [01_FIPS](01_FIPS_CRYPTO.md) |
| SC-13 Cryptographic Protection | ⚠️ **Partial** | BC-FIPS provider registered; Azul Zulu JRE (FIPS-capable); PBKDF2 done; CSPRNG (`SecureRandom`) for backup code generation (80-bit entropy); KMS for JWT pending | [01_FIPS](01_FIPS_CRYPTO.md) |
| SC-28 Protection at Rest | 🔨 **Done** | AES-256-GCM field-level encryption on `cv_candidates` (email, phone, city) + `credentials.mfa_secret` (TOTP seed) via `PiiEncryptionConverter` JPA converter; migration-safe `ENC:` prefix passthrough for existing plaintext rows; ephemeral key for dev, `PII_ENCRYPTION_KEY` env var for prod | [08_DATA](08_DATA_PROTECTION.md) |

### SI — System and Information Integrity

| Control | Status | Remaining Gap | Doc |
|---------|--------|---------------|-----|
| SI-2 Flaw Remediation | 🔨 **Done** | GitHub Actions: OWASP Dependency-Check (SCA), Semgrep (SAST), Trivy (container), npm audit **implemented** | [06_SCANNING](06_SECURITY_SCANNING.md) |
| SI-3 Malicious Code Protection | 🔨 **Done** | Inline ClamAV clamd INSTREAM scan on every upload; `MalwareScanService` abstraction with fail-closed default (FedRAMP SI-3); `NoOpMalwareScanService` for dev; audit event on infected upload | [06_SCANNING](06_SECURITY_SCANNING.md) |
| SI-4 System Monitoring | ⚠️ Improved | 19 security-event Micrometer counters (auth, token, session, malware, MFA: `security.mfa.enrolled/disabled/verify.failed/backup.code.used/replay.attempt/lockout`); structured NDJSON audit log ready for SIEM tail ingestion; CloudWatch/ELK subscription still pending GovCloud | [05_AUDIT](05_AUDIT_SIEM.md) |
| SI-10 Input Validation | ✅ | Jakarta Bean Validation; JPA parameterized | — |
| SI-12 Information Management & Retention | 🔨 **Done** | `CvRetentionService` runs monthly (01:00 on the 1st); anonymises PII fields (`fullName`, `email`, `phone`, `city`, `country`, `linkedinUrl`, `summary`) of candidates older than `CV_RETENTION_DAYS` (default 1095 = 3 years) that are not `IN_PROCESS`, `OFFERED`, or `HIRED`; `anonymized_at` column (V36) marks processed records; `CV_BATCH_ANONYMIZED` audit event with count + cutoff | — |

### CM — Configuration Management

| Control | Status | Remaining Gap | Doc |
|---------|--------|---------------|-----|
| CM-6 Configuration Settings | 🔨 **Done** | `SecurityConfigValidator` (`ApplicationRunner`) checks 5 critical settings at startup: PII key, JWT keystore path, cookie secure, malware scan, internal API key; each maps to its FedRAMP control reference; logs per-gap warnings; emits `SECURITY_CONFIG_CHECK_PASSED/FAILED` audit event; `app.security.enforce-config-check=true` (env `ENFORCE_CONFIG_CHECK`) causes fail-fast in prod; dev default is warn-only | — |
| CM-7 Least Functionality | 🔨 **Done** | Actuator exposure restricted to `health,info,loggers`; `/actuator/loggers/**` gate requires `PERM_analyticsView` (CM-7 bonus via Sprint 23) | — |

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
| P1-4 | No malware scan on uploads (SI-3) | ✅ **Done** | Inline ClamAV clamd TCP INSTREAM scan + `MalwareScanService` abstraction; fail-closed by default; audit log on block; scan status on `documents.scan_status` | [06_SCANNING](06_SECURITY_SCANNING.md) |
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
| P2-6 | No field-level encryption on `cv_candidates` PII (SC-28) | ✅ **Done** | [08_DATA](08_DATA_PROTECTION.md) |
| P2-7 | External LLM processes candidate PII (SA-9) | ✅ **Done** | [08_DATA](08_DATA_PROTECTION.md) |
| P2-8 | No audit/report export (CA-7) | ✅ **Done** | [05_AUDIT](05_AUDIT_SIEM.md) |

---

## Implementation Progress Scoreboard

| Layer | Done | In Progress | Not Started | Total |
|-------|:----:|:-----------:|:-----------:|:-----:|
| P0 — Absolute Blockers | 1 | 1 | 3 | 5 |
| P1 — Required Before ATO | 8 | 1 | 0 | 9 |
| P2 — 90-Day Post-ATO | 3 | 0 | 5 | 8 |
| **All priorities** | **12** | **2** | **8** | **22** |

**Controls resolved or materially improved (sprint 1):** AC-8, AC-11, AC-12, AU-3, AU-5, IA-3, IA-5(1), SI-2, P1-6, P1-7, P1-9  
**Controls resolved or materially improved (sprint 2):** IA-2(1), IA-2(2), AC-10, SC-7 (script CSP)  
**Controls resolved or materially improved (sprint 3):** SI-3 (inline ClamAV malware scan, fail-closed, audit on block)
**Controls resolved or materially improved (sprint 4):** SC-28 (AES-256-GCM field-level PII encryption on cv_candidates), SA-9 (PII tokenisation before LLM — email, phone, LinkedIn, GitHub masked with reversible tokens)
**Controls resolved or materially improved (sprint 5):** CA-7 (audit CSV export — date-range + action filter, streaming cursor, 366-day cap, PERM_analyticsView gated)
**Controls resolved or materially improved (sprint 6):** AU-2 (complete recruitment pipeline audit coverage — JobPosting, JobApplication, Interview, HiringRequest, CvShare)
**Controls resolved or materially improved (sprint 7):** AC-2 (session revocation on user termination/deactivation — all active JWTs immediately denied)
**Controls resolved or materially improved (sprint 8):** SI-4 (13 pre-registered Micrometer counters for security events: auth outcomes, token lifecycle, session idle/revocation, malware scan results)
**Controls resolved or materially improved (sprint 9):** AU-9 (three-layer audit immutability: Hibernate `@Immutable` + JPA listener + repository guards at app layer; DB trigger blocks DELETE/UPDATE at SQL layer)
**Controls resolved or materially improved (sprint 10):** SC-5 (per-IP token-bucket rate limiter on login/MFA/refresh; 429 + Retry-After; `security.rate.limit.exceeded` counter for Prometheus alerting)
**Controls resolved or materially improved (sprint 11):** IA-5(3) (NIST SP 800-63B password policy: min 12 / max 128, common-password blocklist, user-attribute checks, structured 422 response with all violations listed)  
**Controls resolved or materially improved (sprint 12):** AU-11 (scheduled monthly archival to NDJSON.gz + bulk purge; V31 maintenance-mode DB trigger bypass; configurable `AUDIT_RETENTION_DAYS`)  
**Controls resolved or materially improved (sprint 13):** IA-5(1)(h) (password history: last-5 reuse block on `changePassword`; initial hash recorded on `create`; pruning keeps table bounded; `PASSWORD_HISTORY_COUNT` configurable)  
**Controls resolved or materially improved (sprint 14):** AU-6, SI-4 (SIEM-ready structured audit log: `StructuredArguments.kv()` JSON line per event on `AUDIT` logger; rolling NDJSON file appender; Logstash TCP in prod; `logback-test.xml` prevents file I/O in unit tests)  
**Controls resolved or materially improved (sprint 15):** AC-7 (admin account unlock: `POST /{id}/unlock` clears lockout early; `GET /{id}/lock-status` shows current state; `PERM_usersEdit` gate; `USER_ACCOUNT_UNLOCKED` audit event; `security.auth.account.unlocked` counter)  
**Controls resolved or materially improved (sprint 16):** SC-5 (Redis-backed distributed rate limiter replaces in-memory token bucket; eliminates memory leak; all pods share counters; `Retry-After` from Redis TTL; 14 tests covering pass-through, 429, IP isolation, MFA shared key, TTL fallback)  
**Controls resolved or materially improved (sprint 20):** AC-9 (previous logon notification: `previous_login_at` column on credentials; stamped from `lastLoginAt` before each overwrite; `UserInfo.previousLoginAt` field returned in every auth response and `getMe`; 2 new tests)  
**Controls resolved or materially improved (sprint 19):** AU-2 (closed 3 missing audit events: `USER_CREATED` on provisioning, `USER_UPDATED` on name/role/status changes that don't trigger deactivation, `USER_PASSWORD_CHANGED` on credential rotation; `AuditService` added to `AuthService`)  
**Controls resolved or materially improved (sprint 18):** AC-5 (separation of duties: `update`, `delete`, `unlock` in `UserService` throw `ForbiddenException("AC-5: …")` when actor == target; prevents unilateral self-privilege-escalation or self-lockout-bypass; 3 tests)  
**Controls resolved or materially improved (sprint 34):** SC-8(1) + SC-7 + SC-28 (security response headers: `Strict-Transport-Security: max-age=31536000; includeSubDomains`, `Permissions-Policy` blocking camera/mic/geolocation/payment/usb, `Cross-Origin-Opener-Policy: same-origin`, `Cache-Control: no-store, max-age=0` on all `/api/**` paths; 6 new `CspFilterTest` tests; 756 total, BUILD SUCCESS)  
**Controls resolved or materially improved (sprint 33):** SC-28 + SC-13 (TOTP secret encryption at rest: `@Convert(PiiEncryptionConverter)` on `Credential.mfaSecret` — AES-256-GCM encrypts the TOTP seed in `credentials` table; migration-safe `ENC:` prefix passthrough; backup code RNG upgraded from `java.util.Random(millis)` to `SecureRandom`, entropy raised from 40-bit to 80-bit; 2 new tests — format/entropy/uniqueness assertion + reflection annotation verification; 751 total, BUILD SUCCESS)  
**Controls resolved or materially improved (sprint 32):** AC-7 (MFA brute-force lockout: `verifyChallenge` counts failures in Redis `mfa:fail:{userId}` with sliding 900 s TTL; locks account via `credential.setLockedUntil()` after `MFA_MAX_FAILURES` (default 5) consecutive failures; resets counter on any success; `MFA_LOCKOUT` audit event + `security.mfa.lockout` counter; 4 new `MfaServiceTest` tests + 1 `SecurityEventRecorderTest` test; 749 total, BUILD SUCCESS)  
**Controls resolved or materially improved (sprint 31):** IA-2(8) (replay-resistant authentication: Redis `mfa:used:{userId}:{code}` key, 90 s TTL, blocks TOTP replay in `verifyChallenge`, `confirmSetup`, `disable`; `MFA_REPLAY_ATTEMPT` audit event + `security.mfa.replay.attempt` counter; 4 new replay tests in `MfaServiceTest` + 1 in `SecurityEventRecorderTest`; 744 total, BUILD SUCCESS, 95.1% branch coverage)  
**Controls resolved or materially improved (sprint 30):** AU-2 + SI-4 (MFA audit events: `MFA_ENROLLED`, `MFA_DISABLED`, `MFA_VERIFY_FAILED`, `MFA_BACKUP_CODE_USED` emitted from `MfaService`; 4 new `SecurityEventRecorder` counters — `security.mfa.enrolled/disabled/verify.failed/backup.code.used`; 4 new `MfaServiceTest` audit-event/metric tests + 4 new `SecurityEventRecorderTest` tests; 739 total, BUILD SUCCESS, 95.1% branch coverage)  
**Controls resolved or materially improved (sprint 29):** IA-5(1)(c) (admin credential reset: `POST /api/v1/users/{id}/reset-password`; AC-5 self-reset guard; temp password + `mustChangePassword=true` + session revocation + policy/history validation + `USER_PASSWORD_ADMIN_RESET` audit; `AdminResetPasswordRequest` DTO; 6 service tests + 1 controller test; 731 total, BUILD SUCCESS)  
**Controls resolved or materially improved (sprint 28):** IA-5(1)(b) (minimum password lifetime: `changePassword()` blocks changes within `PASSWORD_MIN_AGE_DAYS` (default 1) of the last change; prevents password cycling to defeat history control; `forceChangePassword()` bypasses check; 3 new tests in `AuthServiceTest`; setUp now sets `passwordMinAgeDays=0` to isolate unrelated tests; 724 total, BUILD SUCCESS)  
**Controls resolved or materially improved (sprint 27):** CM-6 (security configuration baseline validator: `SecurityConfigValidator` checks 5 settings — PII encryption key, JWT keystore path, cookie secure flag, malware scan enabled, internal API key; warn-only by default; `ENFORCE_CONFIG_CHECK=true` causes `IllegalStateException` at startup in prod; `SECURITY_CONFIG_CHECK_PASSED/FAILED` audit event emitted on every startup; 12 tests; 721 total, BUILD SUCCESS)  
**Controls resolved or materially improved (sprint 26):** IA-5(1)(f) (force password change at first use: `must_change_password` column V37; set `true` on every admin-provisioned account in `UserService.create()`; `issueTokensForUser` gate checks flag OR date-based expiry — both route through existing `AuthResponse.passwordExpired` + `forceChangePassword` flow; flag cleared after successful change; 2 new tests in `AuthServiceTest` + credential captor assertion in `UserServiceTest`; 709 tests total, BUILD SUCCESS)  
**Controls resolved or materially improved (sprint 25):** AU-6 (audit query endpoint: `GET /api/v1/admin/audit/events` with five optional filters — from, to, action, actorId, entityType; paginated `PagedResponse<AuditEventResponse>`; page-size capped at 200; ISO-8601 date validation returns 400 on bad input; `AuditEventRepository.search()` JPQL query with nullable params; `AuditQueryService` + `AuditEventResponse` DTO; 6 service tests + 7 controller tests; 707 tests total, all passing)  
**Controls resolved or materially improved (sprint 24):** SI-12 (data retention: `CvRetentionService` monthly scheduler anonymises expired CV PII; `anonymized_at` column V36; skips `IN_PROCESS`/`OFFERED`/`HIRED` candidates; `CV_BATCH_ANONYMIZED` audit event; configurable `CV_RETENTION_DAYS:1095`; 7 tests)  
**Controls resolved or materially improved (sprint 23):** AC-6(9) + CM-7 (privileged function audit: `AuditController.export()` now emits `AUDIT_EXPORT_DOWNLOADED` event with actor/date-range/filter; audit not emitted on validation errors; `/actuator/loggers/**` restricted to `PERM_analyticsView`; 5 new tests + 9 existing tests updated for new `userId` parameter)  
**Controls resolved or materially improved (sprint 22):** IA-11 (re-authentication on privilege change: `UserService.update()` calls `revokeAllSessions()` when role changes; `USER_ROLE_CHANGED` audit event with before/after roleId; eliminates up-to-15-minute JWT window where demoted users retain elevated claims; 2 new tests + updated existing test)  
**Controls resolved or materially improved (sprint 21):** IA-5(1)(d) (password expiry enforcement: `password_changed_at` column; expiry gate in `issueTokensForUser` blocks tokens after 60 days; `PasswordExpireService` Redis token (10-min TTL); `POST /api/v1/auth/force-change-password` issues full session after forced rotation; `USER_PASSWORD_FORCE_CHANGED` audit event; `changePassword` now also stamps `passwordChangedAt`; 9 new tests)  
**Controls resolved or materially improved (sprint 17):** IA-4(e) (automated inactive account deactivation: `InactiveAccountService` `@Scheduled` daily at 03:00 UTC; `last_login_at` column + stamp on successful login; `findActiveUsersInactiveSince()` JPQL query; `USER_AUTO_DEACTIVATED` audit event; sessions revoked; `security.user.auto.deactivated` counter; configurable `ACCOUNT_INACTIVITY_DAYS:90`)

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

### Sprint 3 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| `MalwareScanService` interface + `ScanResult` (CLEAN / INFECTED / ERROR) | `MalwareScanService.java` | SI-3 |
| `ClamdMalwareScanService` — clamd INSTREAM TCP protocol, chunked streaming | `ClamdMalwareScanService.java` | SI-3 |
| `NoOpMalwareScanService` — always CLEAN, active when `MALWARE_SCAN_ENABLED=false` | `NoOpMalwareScanService.java` | SI-3 |
| `MalwareDetectedException` + `GlobalExceptionHandler` handler (422) | `MalwareDetectedException.java`, `GlobalExceptionHandler.java` | SI-3 |
| Inline scan in `DocumentService.upload()` — bytes scanned before DB record created | `DocumentService.java` | SI-3 |
| Fail-closed default (`app.malware.scan.fail-open=false`) — scan error rejects upload | `application.yml`, `DocumentService.java` | SI-3 |
| Audit event `DOCUMENT_MALWARE_BLOCKED` on infected file | `DocumentService.java`, `AuditService.java` | AU-2, SI-3 |
| `scan_status` column on `documents` table | `V27__malware_scan.sql`, `Document.java` | SI-3 |
| `store(String, byte[])` overload on `StorageService` | `StorageService.java` | SI-3 |
| Configuration: `clamd-host`, `clamd-port`, `timeout-ms`, `fail-open` | `application.yml` | SI-3 |

### Sprint 11 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| `PasswordPolicyService` — validates min 12/max 128, common-password blocklist (resource file), user email local-part and name-fragment checks | `PasswordPolicyService.java` (new) | IA-5(3) |
| `common-passwords.txt` — ~200 blocklisted passwords from HIBP/rockyou top lists | `resources/security/common-passwords.txt` (new) | IA-5(3) |
| `PasswordPolicyException` — carries full `List<String> violations` for structured error response | `PasswordPolicyException.java` (new) | IA-5(3) |
| `ErrorResponse.ofPasswordPolicy(violations)` — `PASSWORD_POLICY` 422 response with `violations` array | `ErrorResponse.java`, `GlobalExceptionHandler.java` | IA-5(3) |
| `CreateUserRequest` — `@Size(min=12, max=128)` on password field | `CreateUserRequest.java` | IA-5(3) |
| `ChangePasswordRequest` — `@Size(min=12, max=128)` on newPassword field | `ChangePasswordRequest.java` | IA-5(3) |
| `UserService.create` — calls `passwordPolicyService.validate()` before encoding | `UserService.java` | IA-5(3) |
| `AuthService.changePassword` — looks up user, calls `passwordPolicyService.validate()` | `AuthService.java` | IA-5(3) |
| `PasswordPolicyServiceTest` — 16 tests covering length, blocklist, user-attribute, null inputs, multi-violation | `PasswordPolicyServiceTest.java` (new) | IA-5(3) |

### Sprint 10 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| `RateLimitFilter` — per-IP token-bucket on `POST /login`, `/refresh`, `/mfa/verify`, `/mfa/enroll/init`, `/mfa/enroll/confirm`; returns 429 + `Retry-After` | `RateLimitFilter.java` (new) | SC-5 |
| Embedded `TokenBucket` — pure-Java greedy-refill token bucket; no external dependency | `RateLimitFilter.java` | SC-5 |
| `SecurityEventRecorder.recordRateLimitExceeded()` — `security.rate.limit.exceeded` Micrometer counter | `SecurityEventRecorder.java` | SC-5, SI-4 |
| `SecurityConfig` — `rateLimitFilter` registered first in filter chain before `CspFilter` | `SecurityConfig.java` | SC-5 |
| `RateLimitFilterTest` — 12 tests: pass-through, 429 on exhaustion, shared MFA quota, IP isolation, `X-Forwarded-For`, `Retry-After`, `TokenBucket` unit tests | `RateLimitFilterTest.java` (new) | SC-5 |

### Sprint 16 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| `RateLimitFilter` — replaced `ConcurrentHashMap<String, TokenBucket>` + embedded `TokenBucket` class with `StringRedisTemplate`-backed fixed-window counter; `tryConsume(bucketKey, capacity, windowSeconds)` does INCR + conditional EXPIRE; comment explains INCR/EXPIRE atomicity trade-off | `RateLimitFilter.java` | SC-5 |
| Redis key format: `ratelimit:{endpoint}:{ip}`; `Retry-After` = remaining TTL from `getExpire`; fallback to 1 s when TTL unavailable | `RateLimitFilter.java` | SC-5 |
| `RateLimitFilterTest` — 14 tests rewritten with `@Mock StringRedisTemplate` + `ValueOperations`; covers within-limit, at-limit, over-limit, non-rated path, GET pass-through, TTL fallback, IP isolation, `X-Forwarded-For`, MFA shared key, expiry set only on count==1 | `RateLimitFilterTest.java` | SC-5 |

### Sprint 15 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| `LockStatusResponse` DTO — `{ locked, lockedUntil, failedAttempts }` | `LockStatusResponse.java` (new) | AC-7 |
| `UserService.unlock(userId, actorId)` — clears `lockedUntil`/`failedAttempts`, saves credential, records metric and `USER_ACCOUNT_UNLOCKED` audit event | `UserService.java` | AC-7, AC-2 |
| `UserService.getLockStatus(userId)` — returns live lock state from credential | `UserService.java` | AC-7 |
| `UserController POST /{id}/unlock` — `@PreAuthorize("hasAuthority('PERM_usersEdit')")`, returns 204 | `UserController.java` | AC-7 |
| `UserController GET /{id}/lock-status` — `@PreAuthorize("hasAuthority('PERM_usersView')")`, returns `LockStatusResponse` | `UserController.java` | AC-7 |
| `SecurityEventRecorder.recordAccountUnlocked()` — `security.auth.account.unlocked` counter, pre-registered at startup | `SecurityEventRecorder.java` | AC-7, SI-4 |
| Tests — `unlock_clearsLock`, `unlock_throws_*`, `getLockStatus_locked/unlocked/expired`, controller delegation tests, counter test | `UserServiceTest.java`, `UserControllerTest.java`, `SecurityEventRecorderTest.java` | AC-7 |

### Sprint 14 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| `AuditService` — adds static `AUDIT_LOG = LoggerFactory.getLogger("AUDIT")`; emits `AUDIT_LOG.info("audit_event", kv(...))` after every DB save with 10 structured fields | `AuditService.java` | AU-6, SI-4 |
| `logback-spring.xml` — adds `AUDIT_FILE` rolling appender (`LogstashEncoder`, daily roll, 90-day history, 5 GB cap); dev profile routes `AUDIT` → CONSOLE; default routes → `AUDIT_FILE`; prod routes → `AUDIT_FILE` + `LOGSTASH` TCP | `logback-spring.xml` | AU-6 |
| `logback-test.xml` — routes `AUDIT` logger to CONSOLE in tests; prevents `RollingFileAppender` from attempting `/app/logs` writes during unit tests | `src/test/resources/logback-test.xml` (new) | — |
| `AuditServiceTest` — 3 new tests: structured log line emitted after save; actor/entity/event IDs present in args; no log line emitted when DB save throws | `AuditServiceTest.java` | AU-6 |

### Sprint 34 — 2026-05-30

| Item | Files Changed | Controls |
|------|--------------|---------|
| `CspFilter` — `Strict-Transport-Security: max-age=31536000; includeSubDomains` on every response | `CspFilter.java` | SC-8(1) |
| `CspFilter` — `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()` restricts all browser feature APIs | `CspFilter.java` | SC-7, SC-18 |
| `CspFilter` — `Cross-Origin-Opener-Policy: same-origin` prevents cross-origin opener attacks | `CspFilter.java` | SC-7 |
| `CspFilter` — `Cache-Control: no-store, max-age=0` on all `/api/**` responses prevents browser caching of tokens, user data, and PII | `CspFilter.java` | SC-28 |
| `CspFilterTest` — 6 new tests: HSTS value, Permissions-Policy content, COOP value, Cache-Control on API path, no Cache-Control on non-API path | `CspFilterTest.java` | SC-8(1), SC-7 |

### Sprint 33 — 2026-05-30

| Item | Files Changed | Controls |
|------|--------------|---------|
| `Credential.java` — `@Convert(converter = PiiEncryptionConverter.class)` on `mfaSecret` field; AES-256-GCM transparently encrypts on JPA write, decrypts on read; no schema change (column already `TEXT`) | `Credential.java` | SC-28 |
| `MfaService.generateBackupCodes` — replaced `new Random(System.currentTimeMillis())` with `SecureRandom`; entropy raised from 5 bytes (40-bit) to 10 bytes (80-bit) per code; `secureRandom` instance variable | `MfaService.java` | SC-13 |
| `MfaServiceTest` — `confirmSetup_generatesBackupCodesWithSufficientEntropy`: asserts 10 codes of exactly 20 uppercase hex chars, all distinct; `credential_mfaSecret_isEncryptedAtRest`: reflection check that `@Convert(PiiEncryptionConverter)` is present on field | `MfaServiceTest.java` | SC-28, SC-13 |

### Sprint 32 — 2026-05-30

| Item | Files Changed | Controls |
|------|--------------|---------|
| `application.yml` — `app.mfa.max-failures` (default 5) and `app.mfa.lockout-seconds` (default 900) | `application.yml` | AC-7 |
| `MfaService` — `incrementMfaFailures(userId)`: increments `mfa:fail:{userId}` Redis counter, sets 900 s TTL on first failure; `clearMfaFailures(userId)`: deletes key on any successful verification | `MfaService.java` | AC-7 |
| `MfaService.verifyChallenge` — calls `clearMfaFailures` on TOTP and backup-code success; on failure, increments counter and locks account when threshold reached | `MfaService.java` | AC-7 |
| `SecurityEventRecorder` — `security.mfa.lockout` counter pre-registered at startup | `SecurityEventRecorder.java` | AC-7, SI-4 |
| `MfaServiceTest` — 4 new tests: no-lock below threshold, lock + audit + metric at threshold, TTL set on first failure, failure counter cleared on success | `MfaServiceTest.java` | AC-7 |
| `SecurityEventRecorderTest` — pre-registration assertion + `recordMfaLockout_incrementsCounter` | `SecurityEventRecorderTest.java` | SI-4 |

### Sprint 31 — 2026-05-30

| Item | Files Changed | Controls |
|------|--------------|---------|
| `MfaService` — `isCodeAlreadyUsed(userId, code)` checks Redis `mfa:used:{userId}:{code}` key; `markCodeUsed(userId, code)` stores key with 90 s TTL | `MfaService.java` | IA-2(8) |
| `MfaService.verifyChallenge` — replay check after TOTP succeeds; marks code used before returning; `MFA_REPLAY_ATTEMPT` audit + metric on blocked replay | `MfaService.java` | IA-2(8), AU-2 |
| `MfaService.confirmSetup` — same replay check after TOTP verification, before enabling MFA | `MfaService.java` | IA-2(8) |
| `MfaService.disable` — same replay check before clearing MFA credentials | `MfaService.java` | IA-2(8) |
| `SecurityEventRecorder` — `security.mfa.replay.attempt` counter pre-registered at startup | `SecurityEventRecorder.java` | SI-4 |
| `MfaServiceTest` — 4 new tests: replay rejection in `verifyChallenge`/`confirmSetup`/`disable`; code-marked-used verification | `MfaServiceTest.java` | IA-2(8) |
| `SecurityEventRecorderTest` — `allCounters_preRegistered_atZero` updated; `recordMfaReplayAttempt_incrementsCounter` | `SecurityEventRecorderTest.java` | SI-4 |

### Sprint 30 — 2026-05-30

| Item | Files Changed | Controls |
|------|--------------|---------|
| `MfaService.confirmSetup` — emits `MFA_ENROLLED` audit event + `recordMfaEnrolled()` counter after successful TOTP confirmation | `MfaService.java` | AU-2, SI-4 |
| `MfaService.disable` — emits `MFA_DISABLED` audit event + `recordMfaDisabled()` counter after credential cleared | `MfaService.java` | AU-2, SI-4 |
| `MfaService.verifyChallenge` — emits `MFA_BACKUP_CODE_USED` + `recordMfaBackupCodeUsed()` on backup-code path; `MFA_VERIFY_FAILED` + `recordMfaVerifyFailed()` before throw on both-fail path | `MfaService.java` | AU-2, SI-4 |
| `SecurityEventRecorder` — 4 new pre-registered counters: `security.mfa.enrolled`, `security.mfa.disabled`, `security.mfa.verify.failed`, `security.mfa.backup.code.used` | `SecurityEventRecorder.java` | SI-4 |
| `MfaServiceTest` — added `@Mock AuditService` + `@Mock SecurityEventRecorder`; updated constructor; 4 new audit-event/metric tests | `MfaServiceTest.java` | AU-2, SI-4 |
| `SecurityEventRecorderTest` — 4 new counter pre-registration assertions + 4 new increment tests; `allCounters_preRegistered_atZero` updated | `SecurityEventRecorderTest.java` | SI-4 |

### Sprint 20 — 2026-05-30

| Item | Files Changed | Controls |
|------|--------------|---------|
| `V34__credential_previous_login.sql` — `ALTER TABLE credentials ADD COLUMN previous_login_at TIMESTAMPTZ` | `V34__credential_previous_login.sql` (new) | AC-9 |
| `Credential.java` — `previousLoginAt` field | `Credential.java` | AC-9 |
| `AuthService.login` — `credential.setPreviousLoginAt(credential.getLastLoginAt())` before overwriting `lastLoginAt` | `AuthService.java` | AC-9 |
| `UserInfo` record — added `previousLoginAt Instant` field | `UserInfo.java` | AC-9 |
| `AuthService.buildUserInfo` — fetches credential and passes `previousLoginAt` to `UserInfo` constructor | `AuthService.java` | AC-9 |
| Updated `UserInfo` constructor calls to 7-arg form | `AuthControllerTest.java`, `MfaControllerTest.java`, `ProfileControllerTest.java` | AC-9 |
| `AuthServiceTest` — `login_setsPreviousLoginAt_fromExistingLastLoginAt`, `getMe_includesPreviousLoginAt_inUserInfo` | `AuthServiceTest.java` | AC-9 |

### Sprint 19 — 2026-05-30

| Item | Files Changed | Controls |
|------|--------------|---------|
| `UserService.create(request, actorId)` — added `actorId` param; emits `USER_CREATED` audit event after full provisioning | `UserService.java` | AU-2 |
| `UserService.update` — captures `oldName` before mutation; emits `USER_UPDATED` in the else branch (non-deactivation path) | `UserService.java` | AU-2 |
| `AuthService` — added `AuditService` dependency; `changePassword()` emits `USER_PASSWORD_CHANGED` after credential save | `AuthService.java` | AU-2 |
| `UserController.create` — passes `actorId` from `@AuthenticationPrincipal` to `userService.create()` | `UserController.java` | AU-2 |
| `UserServiceTest` — updated `create` call sites to `create(req, ACTOR_ID)`; added `USER_CREATED` verify to `create_succeeds`; added `update_emitsUserUpdated_whenNotDeactivating` test | `UserServiceTest.java` | AU-2 |
| `UserControllerTest` — updated 3 `create` stubs to `create(eq(request), any())` | `UserControllerTest.java` | AU-2 |
| `AuthServiceTest` — added `@Mock AuditService`; added `USER_PASSWORD_CHANGED` audit verify to `changePassword_succeeds` | `AuthServiceTest.java` | AU-2 |

### Sprint 18 — 2026-05-30

| Item | Files Changed | Controls |
|------|--------------|---------|
| `UserService.update` — guard: `if (id.equals(actorId)) throw ForbiddenException("AC-5: …")` | `UserService.java` | AC-5 |
| `UserService.delete` — guard: `if (id.equals(actorId)) throw ForbiddenException("AC-5: …")` | `UserService.java` | AC-5 |
| `UserService.unlock` — guard: `if (userId.equals(actorId)) throw ForbiddenException("AC-5: …")` | `UserService.java` | AC-5 |
| Tests — `update_throws_whenActorIsTarget`, `delete_throws_whenActorIsTarget`, `unlock_throws_whenActorIsTarget` | `UserServiceTest.java` | AC-5 |

### Sprint 17 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| `V33__credential_last_login.sql` — `ALTER TABLE credentials ADD COLUMN last_login_at TIMESTAMPTZ` | `V33__credential_last_login.sql` (new) | IA-4(e) |
| `Credential.java` — `lastLoginAt` field | `Credential.java` | IA-4(e) |
| `AuthService.login` — stamps `credential.setLastLoginAt(Instant.now())` after successful password match before saving | `AuthService.java` | IA-4(e) |
| `UserRepository.findActiveUsersInactiveSince` — JPQL: active + `createdAt < cutoff` + `EXISTS` subquery checking `lastLoginAt IS NULL OR lastLoginAt < cutoff` | `UserRepository.java` | IA-4(e) |
| `InactiveAccountService` — `@Scheduled(cron = "0 0 3 * * ?")` daily 03:00 UTC; finds inactive accounts, sets status=inactive, revokes sessions, emits `USER_AUTO_DEACTIVATED` audit event, records metric | `InactiveAccountService.java` (new) | IA-4(e) |
| `SecurityEventRecorder.recordUserAutoDeactivated()` — `security.user.auto.deactivated` counter | `SecurityEventRecorder.java` | IA-4(e), SI-4 |
| `application.yml` — `app.account.inactivity.deactivate-after-days` (default 90, env `ACCOUNT_INACTIVITY_DAYS`) | `application.yml` | IA-4(e) |
| `InactiveAccountServiceTest` — 8 tests: no-op on empty, sets inactive status, emits audit event, records metric per user, revokes active sessions, session revocation metric, no session metric when no sessions, cutoff calculation | `InactiveAccountServiceTest.java` (new) | IA-4(e) |
| `AuthServiceTest` — `login_setsLastLoginAt_onSuccessfulCredentialValidation` test | `AuthServiceTest.java` | IA-4(e) |
| `SecurityEventRecorderTest` — `recordUserAutoDeactivated_incrementsCounter` + `allCounters_preRegistered_atZero` updated | `SecurityEventRecorderTest.java` | IA-4(e) |

### Sprint 13 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| `V32__password_history.sql` — `password_history` table with `user_id`, `password_hash`, `created_at`; composite index on `(user_id, created_at DESC)` | `V32__password_history.sql` (new) | IA-5(1)(h) |
| `PasswordHistory` entity — append-only, no setters | `PasswordHistory.java` (new) | IA-5(1)(h) |
| `PasswordHistoryRepository` — `findByUserIdOrderByCreatedAtDesc` | `PasswordHistoryRepository.java` (new) | IA-5(1)(h) |
| `PasswordHistoryService` — `checkNotReused(userId, raw)` throws `PasswordPolicyException` on reuse; `record(userId, hash)` saves and prunes to `historyCount` | `PasswordHistoryService.java` (new) | IA-5(1)(h) |
| `AuthService.changePassword` — calls `checkNotReused` before encoding, `record` after saving; check is ordered after policy validation | `AuthService.java` | IA-5(1)(h) |
| `UserService.create` — records initial encoded hash after credential save | `UserService.java` | IA-5(1)(h) |
| `application.yml` — `app.password.history.count` (default 5, env `PASSWORD_HISTORY_COUNT`) | `application.yml` | IA-5(1)(h) |
| `PasswordHistoryServiceTest` — 9 tests: no history, pass, fail, window boundary, violation message, save, prune on excess, no prune within limit, exact-limit no-prune | `PasswordHistoryServiceTest.java` (new) | IA-5(1)(h) |

### Sprint 12 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| `V31__audit_retention_maintenance.sql` — updates `fn_audit_events_immutable` to allow DELETE when `app.audit_maintenance = 'true'` session GUC is set | `V31__audit_retention_maintenance.sql` (new) | AU-11, AU-9 |
| `AuditEventRepository.deleteByOccurredAtBefore` — `@Modifying @Query` bypasses Java-layer immutability guards; DB trigger enforces auth via session GUC | `AuditEventRepository.java` | AU-11 |
| `AuditRetentionService` — `@Scheduled(cron = "0 0 2 1 * ?")` monthly job: streams events to NDJSON.gz archive, then purges within a maintenance transaction | `AuditRetentionService.java` (new) | AU-11 |
| `application.yml` — `app.audit.retention.online-days` (default 365) and `app.audit.archive-path` (default `/app/audit-archives`) | `application.yml` | AU-11 |
| `AuditRetentionServiceTest` — 8 tests: NDJSON.gz write, multi-event, empty run, nested dir creation, GUC ordering, skip-on-archive-failure, skip-purge-on-zero-archived | `AuditRetentionServiceTest.java` (new) | AU-11 |

### Sprint 9 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| `AuditImmutabilityListener` — JPA `@PreRemove`/`@PreUpdate` listener throws `UnsupportedOperationException` | `AuditImmutabilityListener.java` (new) | AU-9 |
| `AuditEvent` — added `@EntityListeners(AuditImmutabilityListener.class)` + Hibernate `@Immutable` | `AuditEvent.java` | AU-9 |
| `AuditEventRepository` — `deleteById`, `delete`, `deleteAll`, `deleteAllById` overrides throw immediately | `AuditEventRepository.java` | AU-9 |
| `V30__audit_immutability.sql` — DB trigger `fn_audit_events_immutable` blocks DELETE/UPDATE on `audit_events`; conditional REVOKE on `hrportal_app` role | `V30__audit_immutability.sql` (new) | AU-9 |
| `AuditImmutabilityListenerTest` — 7 tests: listener `@PreRemove`/`@PreUpdate` + all 5 repository guard overrides | `AuditImmutabilityListenerTest.java` (new) | AU-9 |

### Sprint 8 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| `SecurityEventRecorder` — 13 pre-registered Micrometer counters at startup | `SecurityEventRecorder.java` (new) | SI-4 |
| `AuthService` — counters for login success/failure, bad credentials, account locked/inactive, session limit, lockout, token issued | `AuthService.java` | SI-4 |
| `JwtCookieAuthFilter` — counter on token denied and idle session | `JwtCookieAuthFilter.java` | SI-4 |
| `DocumentService` — counter on malware blocked and scan error | `DocumentService.java` | SI-4 |
| `UserService.revokeAllSessions` — counter on bulk session revocation with count argument | `UserService.java` | SI-4 |
| `SecurityEventRecorderTest` — 16 tests verifying every counter increments correctly using `SimpleMeterRegistry` | `SecurityEventRecorderTest.java` (new) | SI-4 |
| `@Mock SecurityEventRecorder` added to 4 test classes | `AuthServiceTest`, `JwtCookieAuthFilterTest`, `DocumentServiceTest`, `UserServiceTest` | SI-4 |

### Sprint 7 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| `TokenDenylistService.deny(jti, maxTtlSeconds)` — overload for forced revocation without token claims | `TokenDenylistService.java` | AC-2, AC-12 |
| `SessionActivityService.getSessionJtis(userId)` — returns all registered jtis for a user | `SessionActivityService.java` | AC-2 |
| `SessionActivityService.clearUserSessions(userId)` — deletes the user's session set key | `SessionActivityService.java` | AC-2 |
| `UserService.revokeAllSessions(userId)` — denies all jtis + clears activity and session-set keys | `UserService.java` | AC-2 |
| `UserService.delete(id, actorId)` — revokes sessions before soft-delete; `USER_DELETED` audit event | `UserService.java` | AC-2, AU-2 |
| `UserService.update(id, req, actorId)` — revokes sessions when status changes active → inactive; `USER_DEACTIVATED` audit event | `UserService.java` | AC-2, AU-2 |
| `UserController` — threads `actorId` from `@AuthenticationPrincipal` for `update` and `delete` | `UserController.java` | AC-2 |
| 4 new tests; fixed `UserServiceTest` and `UserControllerTest` signatures | `UserServiceTest.java`, `UserControllerTest.java` | AC-2 |

### Sprint 6 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| Audit events on `JobPostingService` — `JOB_POSTING_CREATED`, `JOB_POSTING_UPDATED`, `JOB_POSTING_CLOSED`, `JOB_POSTING_SKILLS_UPDATED` | `JobPostingService.java`, `JobPostingController.java` | AU-2 |
| Audit events on `JobApplicationService` — `APPLICATION_CREATED`, `APPLICATION_BATCH_CREATED`, `APPLICATION_STAGE_MOVED` | `JobApplicationService.java` | AU-2 |
| Audit events on `InterviewService` — `INTERVIEW_SCHEDULED`, `INTERVIEW_FEEDBACK_SUBMITTED` | `InterviewService.java` | AU-2 |
| Audit events on `HiringRequestService` — `HIRING_REQUEST_CREATED`, `HIRING_REQUEST_STATUS_UPDATED`, `HIRING_REQUEST_LINKED` | `HiringRequestService.java` | AU-2 |
| Audit events on `CvShareService` — `CV_SHARED`, `CV_IMPRESSION_SUBMITTED` | `CvShareService.java` | AU-2 |
| Test fixes: `@Mock AuditService` added to 7 service/controller tests; `actorId` param threaded through `update`/`delete`/`setSkills` | `JobPostingServiceTest.java`, `JobPostingControllerTest.java`, `JobPostingSkillServiceTest.java`, `JobApplicationServiceTest.java`, `InterviewServiceTest.java`, `HiringRequestServiceTest.java`, `CvShareServiceTest.java` | AU-2 |

### Sprint 5 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| `AuditEventRepository` — `streamByDateRange` and `streamByDateRangeAndAction` with cursor fetch-size 500 | `AuditEventRepository.java` | CA-7 |
| `AuditExportService` — streams CSV rows via JPA cursor; RFC-4180 escaping; 366-day range cap | `AuditExportService.java` | CA-7 |
| `AuditController` — `GET /api/v1/admin/audit/export?from=&to=&action=`; gated on `PERM_analyticsView`; `Content-Disposition: attachment`, `Cache-Control: no-store` | `AuditController.java` | CA-7 |
| Flyway V29: `idx_ae_occurred_at` for date-range export queries | `V29__audit_export_index.sql` | CA-7 |
| 22 unit tests (service + controller) | `AuditExportServiceTest.java`, `AuditControllerTest.java` | CA-7 |

### Sprint 4 — 2026-05-29

| Item | Files Changed | Controls |
|------|--------------|---------|
| `AesFieldEncryptionService` — AES-256-GCM, random IV per encryption, `ENC:` prefix, migration-safe passthrough | `AesFieldEncryptionService.java` | SC-28 |
| `PiiEncryptionConverter` JPA `AttributeConverter` with static holder (avoids Spring/Hibernate bootstrap race) | `PiiEncryptionConverter.java` | SC-28 |
| `EncryptionConfig` `@PostConstruct` wires converter to service | `EncryptionConfig.java` | SC-28 |
| `@Convert` on `email`, `phone`, `city` in `CvCandidate` entity | `CvCandidate.java` | SC-28 |
| Flyway V28: widen PII columns to hold Base64-encoded ciphertext | `V28__pii_field_encryption.sql` | SC-28 |
| `app.encryption.pii-key` config; ephemeral key with warning for dev | `application.yml` | SC-28 |
| `PiiTokenizer` — masks email/phone/LinkedIn/GitHub with reversible `__PII_KIND_N__` tokens | `pii_mask.py` | SA-9 |
| `PiiMaskGuard` — input guardrail, runs before LLM call in both pipeline variants | `pii_mask.py`, `pipeline.py` | SA-9 |
| `PiiRestoreGuard` — output guardrail, inline token restore + kind-based fallback | `pii_restore.py`, `pipeline.py` | SA-9 |
| 33 unit tests for tokenizer and both guards | `test_pii_mask.py`, `test_pii_restore.py` | SA-9 |

### Sprint 2 — 2026-05-28

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

### Immediate (sprint 4 candidates)

| Item | Effort | Blocks |
|------|--------|--------|
| ~~MFA enrollment enforcement hard-block~~ | ~~S~~ | ✅ Done |
| ~~Concurrent session count limit~~ | ~~S~~ | ✅ Done |
| ~~CSP nonce (`CspFilter.java`)~~ | ~~M~~ | ✅ Done |
| ~~Inline ClamAV malware scan (SI-3)~~ | ~~M~~ | ✅ Done |
| Field-level PII encryption on `cv_candidates` (SC-28) | M — 2 days | P2-6 |

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
                    ├── P1-4: Malware scan on uploads ✅ DONE
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
