# 05 — Audit Log Hardening + SIEM Integration
> Controls: AU-2 (Partial), AU-3 (Partial), AU-5 (Gap), AU-6 (Gap), AU-9 (Partial), AU-11 (Partial)
> Priority: P1-2
> Effort: L — 3–5 weeks

---

## Problem Statement

The current `AuditService` has four critical gaps:

| Gap | Issue | Control |
|-----|-------|---------|
| `@Async` on `log()` | Audit failures are silently swallowed — an exception in the async thread is lost | AU-5 |
| No SIEM | Audit events only in app DB; no centralized analysis | AU-6 |
| App DB user can DELETE | No tamper-evidence; audit trail is not WORM-protected | AU-9 |
| 1-year retention | FedRAMP requires 3 years online + 6 years archive | AU-11 |
| Recruitment pipeline not audited | Candidate status changes, CV access not logged | AU-2 |
| No correlation ID / session ID | Can't trace a request across services | AU-3 |

---

## 1. Fix AU-5 — Remove `@Async` for Critical Events

`@Async` with a default executor that silently drops exceptions is unacceptable for audit events.

### Option A — Synchronous audit (simple, recommended for initial compliance)

```java
// AuditService.java — remove @Async entirely for security-critical events
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void log(UUID actorId, String action, String entityType, UUID entityId,
                Map<String, Object> before, Map<String, Object> after, String outcome) {
    // REQUIRES_NEW ensures this commits even if the calling transaction rolls back
    repository.save(AuditEvent.builder()
            .actorId(actorId)
            .action(action)
            .entityType(entityType)
            .entityId(entityId)
            .beforeState(before)
            .afterState(after)
            .outcome(outcome)
            .build());
}
```

If the audit write fails, propagate the exception to the caller — **the business operation should not succeed if auditing fails**.

### Option B — Async with dead-letter queue (for high-throughput paths)

If sync auditing causes latency issues, use an outbox pattern:

```java
// Write to audit_outbox table synchronously (same TX as business op)
// A separate scheduler reads outbox and writes to audit_events + SIEM
// Failed deliveries go to dead-letter with alerting
```

---

## 2. Fix AU-3 — Add Correlation ID and Session ID

### MDC-based correlation

The `MdcUserFilter` already sets `userId` in MDC. Extend it to set `correlationId`:

```java
// MdcUserFilter.java — add correlation ID
@Override
protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
    String correlationId = Optional.ofNullable(req.getHeader("X-Correlation-ID"))
        .orElse(UUID.randomUUID().toString());
    MDC.put("correlationId", correlationId);
    res.setHeader("X-Correlation-ID", correlationId);
    // ... existing userId MDC setup ...
    try { chain.doFilter(req, res); }
    finally { MDC.clear(); }
}
```

### `AuditEvent` entity — add fields

```java
// AuditEvent.java — add to entity
@Column(length = 36)
private String correlationId;

@Column(length = 36)
private String sessionId;    // JWT jti claim (once added to tokens)
```

```sql
-- Flyway migration: V24__audit_event_correlation.sql
ALTER TABLE audit_events
    ADD COLUMN correlation_id  VARCHAR(36),
    ADD COLUMN session_id      VARCHAR(36),
    ADD COLUMN ip_address      INET,
    ADD COLUMN user_agent      TEXT;

CREATE INDEX idx_audit_correlation ON audit_events(correlation_id);
CREATE INDEX idx_audit_actor       ON audit_events(actor_id);
CREATE INDEX idx_audit_occurred    ON audit_events(occurred_at DESC);
```

### Update `AuditService.log()` signature

```java
public void log(UUID actorId, String action, String entityType, UUID entityId,
                Map<String, Object> before, Map<String, Object> after, String outcome) {
    repository.save(AuditEvent.builder()
            .actorId(actorId)
            .action(action)
            .entityType(entityType)
            .entityId(entityId)
            .beforeState(before)
            .afterState(after)
            .outcome(outcome)
            .correlationId(MDC.get("correlationId"))
            .sessionId(MDC.get("sessionId"))
            .ipAddress(MDC.get("ipAddress"))
            .build());
}
```

---

## 3. Fix AU-2 — Expand Audit Coverage

### Minimum required audit events (per FedRAMP AU-2)

```
Authentication:
  AUTH_LOGIN_SUCCESS, AUTH_LOGIN_FAILURE, AUTH_LOGOUT
  AUTH_MFA_ENROLL, AUTH_MFA_VERIFY_SUCCESS, AUTH_MFA_VERIFY_FAILURE
  AUTH_TOKEN_REFRESH, AUTH_TOKEN_REVOKE
  AUTH_SAML_LOGIN_SUCCESS, AUTH_SAML_LOGIN_FAILURE
  AUTH_PASSWORD_CHANGE, AUTH_PASSWORD_RESET

Account management:
  USER_CREATE, USER_UPDATE, USER_DEACTIVATE, USER_ROLE_CHANGE
  ROLE_CREATE, ROLE_UPDATE, ROLE_DELETE
  PERMISSION_GRANT, PERMISSION_REVOKE

Data access:
  CV_UPLOAD, CV_VIEW, CV_DELETE, CV_EXPORT
  CANDIDATE_CREATE, CANDIDATE_UPDATE, CANDIDATE_STATUS_CHANGE
  JOB_POST_CREATE, JOB_POST_PUBLISH, JOB_POST_CLOSE
  REPORT_EXPORT, REPORT_VIEW

System:
  CONFIG_CHANGE, ADMIN_ACTION
  AUDIT_LOG_EXPORT, AUDIT_LOG_ACCESS
```

### Recruitment pipeline — currently NOT audited

Add `@AuditLog` annotation or explicit `auditService.log()` calls to:
- `CandidateService.updateStatus()`
- `CandidateService.viewCv()`
- `JobPostingService.publish()`
- `ReportService.export()`

```java
// Example: CandidateService.java
public void updateStatus(UUID candidateId, CandidateStatus newStatus) {
    Candidate candidate = candidateRepository.findById(candidateId).orElseThrow();
    CandidateStatus oldStatus = candidate.getStatus();

    candidate.setStatus(newStatus);
    candidateRepository.save(candidate);

    auditService.log(
        currentUserId(),
        "CANDIDATE_STATUS_CHANGE",
        "Candidate",
        candidateId,
        Map.of("status", oldStatus),
        Map.of("status", newStatus),
        "success"
    );
}
```

---

## 4. Fix AU-9 — Tamper-Evidence (WORM Protection)

### Database-level protection

```sql
-- Create a dedicated read-only DB user for audit queries
CREATE USER audit_reader WITH PASSWORD '...';
GRANT SELECT ON audit_events TO audit_reader;
-- Application DB user has INSERT only (no UPDATE/DELETE)
REVOKE UPDATE, DELETE ON audit_events FROM hrportal_app;
```

### Append-only enforcement at application level

```java
// AuditEventRepository.java — only allow insert, never update/delete
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    // No deleteBy* or modifying queries allowed
}
```

### S3 WORM archive (preferred for AU-11 retention)

Nightly job exports audit events to S3 with Object Lock (WORM):

```java
// AuditArchiveService.java
@Scheduled(cron = "0 0 2 * * *")  // 2 AM daily
public void archiveAuditEvents() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    List<AuditEvent> events = repository.findByOccurredAtBetween(
        yesterday.atStartOfDay(ZoneOffset.UTC).toInstant(),
        yesterday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
    );
    // Serialize to JSONL, upload to S3 with Object Lock
    String key = "audit/" + yesterday + "/audit-events.jsonl.gz";
    s3Client.putObject(PutObjectRequest.builder()
        .bucket(auditBucket)
        .key(key)
        .objectLockMode(ObjectLockMode.COMPLIANCE)
        .objectLockRetainUntilDate(Instant.now().plus(3 * 365, ChronoUnit.DAYS))
        .build(), RequestBody.fromBytes(compressToGzip(events)));
}
```

### S3 bucket Object Lock configuration (Terraform)

```hcl
resource "aws_s3_bucket_object_lock_configuration" "audit" {
  bucket = aws_s3_bucket.audit_archive.id
  rule {
    default_retention {
      mode = "COMPLIANCE"
      days = 1095   # 3 years (FedRAMP AU-11)
    }
  }
}
```

---

## 5. Fix AU-11 — 3-Year Online Retention

FedRAMP requires audit records be retained for 3 years (online/near-line accessible).

| Tier | Storage | Duration |
|------|---------|----------|
| Hot (DB) | RDS PostgreSQL | 1 year (current) |
| Warm (S3 Standard) | S3 Object Lock COMPLIANCE | Years 1–3 |
| Cold (S3 Glacier) | S3 Glacier (optional archive) | Years 4–6 |

**RDS partition strategy** to keep DB performant while retaining 1 year of hot records:

```sql
-- Partition audit_events by month
CREATE TABLE audit_events (
    ...
) PARTITION BY RANGE (occurred_at);

CREATE TABLE audit_events_2026_01 PARTITION OF audit_events
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
-- Create partitions for each month programmatically
```

---

## 6. SIEM Integration (AU-6)

### Option A — AWS Security Hub + EventBridge (GovCloud native)

```java
// AuditService.java — publish to EventBridge after DB write
@Value("${audit.eventbridge.bus-name}")
private String eventBusBus;

private void publishToEventBridge(AuditEvent event) {
    eventBridgeClient.putEvents(r -> r.entries(e -> e
        .eventBusName(eventBusBus)
        .source("hrportal.audit")
        .detailType(event.getAction())
        .detail(objectMapper.writeValueAsString(event))
    ));
}
```

EventBridge → Security Hub → CloudWatch Logs Insights for analysis.

### Option B — Direct CloudWatch Logs (simpler initial implementation)

```java
// Structured log line picked up by CloudWatch agent
log.info("AUDIT event={} actor={} entity={}/{} outcome={}",
    event.getAction(), event.getActorId(),
    event.getEntityType(), event.getEntityId(),
    event.getOutcome());
```

CloudWatch Logs → Log group with 3-year retention + metric filters for alerting.

### CloudWatch metric alarms for AU-5

```hcl
resource "aws_cloudwatch_metric_alarm" "audit_failure" {
  alarm_name          = "hrportal-audit-write-failure"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "AuditWriteFailure"
  namespace           = "HRPortal/Audit"
  period              = 60
  statistic           = "Sum"
  threshold           = 0
  alarm_actions       = [aws_sns_topic.security_alerts.arn]
}
```

---

## 7. Acceptance Criteria

- [ ] `AuditService.log()` is synchronous — exception propagates to caller
- [ ] DB user `hrportal_app` cannot execute `DELETE` or `UPDATE` on `audit_events`
- [ ] `correlationId` present on all audit events and returned in response headers
- [ ] Candidate status changes appear in `audit_events` table
- [ ] CV upload, view, and delete events logged
- [ ] ADMIN login failures generate an audit event with outcome `failure`
- [ ] S3 audit archive bucket has Object Lock COMPLIANCE enabled
- [ ] S3 nightly archive job runs and produces `audit/YYYY-MM-DD/audit-events.jsonl.gz`
- [ ] CloudWatch alarm fires when an audit write fails
- [ ] 3-year retention policy documented and enforced in S3 Object Lock settings
- [ ] Audit log export endpoint `/api/v1/audit/export` (admin only, audits itself)

---

## 8. Related Documents

- [01_FIPS_CRYPTO.md](01_FIPS_CRYPTO.md) — KMS for audit archive S3 encryption
- [04_GOVCLOUD_MIGRATION.md](04_GOVCLOUD_MIGRATION.md) — CloudWatch and EventBridge in GovCloud
- [07_SESSION_HARDENING.md](07_SESSION_HARDENING.md) — Session ID (JWT `jti`) to populate `session_id` in audit
- [10_CONMON.md](10_CONMON.md) — ConMon uses audit export for evidence
