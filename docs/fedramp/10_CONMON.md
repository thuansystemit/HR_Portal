# 10 — Continuous Monitoring Program
> Controls: CA-7 (Gap), IR-4/6/8 (Gap), CP-2/10 (Gap), CM-1/2 (Gap), PS-4/5 (Gap), AC-1 (Gap)
> Priority: P2
> Effort: M — ongoing

---

## 1. Continuous Monitoring (ConMon) Overview (CA-7)

FedRAMP ConMon requires ongoing visibility into the security posture of the system after ATO is granted. The 3PAO performs an annual assessment; the agency and CSP are responsible for monthly monitoring in between.

### ConMon Cadence

| Activity | Frequency | Owner | Evidence Artifact |
|----------|-----------|-------|------------------|
| Vulnerability scan (OS, containers) | Monthly | DevOps | Trivy scan report |
| Dependency SCA scan | Monthly | DevOps | OWASP DC report |
| Penetration test (limited) | Annual | 3PAO | Pen test report |
| Audit log review | Weekly | SecEng | CloudWatch Insights query results |
| POA&M review/update | Monthly | SecEng | Updated POA&M spreadsheet |
| Security posture report to AO | Monthly | SecEng | ConMon monthly report |
| Configuration baseline check | Monthly | DevOps | AWS Config compliance snapshot |
| Privileged account review | Quarterly | SecEng | IAM access report |
| Backup/recovery test | Quarterly | DevOps | RDS snapshot restore test log |
| Incident response tabletop | Annual | SecEng + Eng leads | After-action report |

### Monthly ConMon Report Template

```
HR Portal — FedRAMP ConMon Report
Month: YYYY-MM
Prepared by: [Security Engineer]
Authorized Official: [AO Name]

1. Executive Summary
   - Open HIGH/CRITICAL vulnerabilities: N
   - New POA&M items this month: N
   - Closed POA&M items this month: N
   - Security incidents this month: N

2. Vulnerability Management
   - Trivy scan date: YYYY-MM-DD
   - OWASP DC scan date: YYYY-MM-DD
   - New findings: [list]
   - Remediated findings: [list]
   - False positives (with justification): [list]

3. POA&M Status
   [Table: ID | Issue | Risk | Milestone | Status | Notes]

4. Audit Log Review
   - Unusual authentication events: [summary]
   - Policy violations detected: [summary]
   - SIEM alerts generated: N

5. Configuration Changes
   - Infrastructure changes this month: [list from CloudTrail]
   - Configuration drift detected: [AWS Config findings]

6. Upcoming Milestones
   [Next month planned activities]
```

---

## 2. POA&M Template (CA-7, CA-5)

Plan of Action and Milestones tracks all identified weaknesses. FedRAMP requires monthly updates.

```markdown
# POA&M — HR Portal FedRAMP Moderate
Last Updated: YYYY-MM-DD

| ID | Control | Weakness | Risk Level | Discovery Date | Scheduled Completion | Milestones | Status | Notes |
|----|---------|----------|------------|----------------|---------------------|------------|--------|-------|
| POA-001 | SC-13 | No FIPS 140-2 validated crypto | HIGH | 2026-05-27 | 2026-12-01 | M1: BC-FIPS added (2026-07-01) / M2: BCrypt migrated (2026-09-01) | In Progress | P0-1 |
| POA-002 | IA-2(1) | No MFA for privileged users | HIGH | 2026-05-27 | 2026-12-01 | M1: TOTP implemented (2026-09-01) | In Progress | P0-2 |
| POA-003 | IA-2(12) | No PIV/CAC support | HIGH | 2026-05-27 | 2027-01-01 | M1: login.gov sandbox (2026-10-01) | Planned | P0-3 |
| POA-004 | CA-6 | No System Security Plan | HIGH | 2026-05-27 | 2026-09-01 | SSP draft in progress | In Progress | P0-4 |
| POA-005 | PE | Commercial AWS (not GovCloud) | HIGH | 2026-05-27 | 2027-01-01 | M1: GovCloud accounts (2026-08-01) | Planned | P0-5 |
```

---

## 3. Incident Response Plan (IR-4, IR-6, IR-8)

### IRP Summary

```
Incident Response Plan — HR Portal
Version: 1.0
Last Reviewed: 2026-05-27

CONTACT DIRECTORY:
  Incident Commander: [Security Lead] — [phone/Signal]
  Technical Lead:     [Backend Lead]  — [phone/Signal]
  AO Contact:         [Agency AO]     — [phone/email]
  US-CERT Reporting:  https://www.cisa.gov/report
  AWS GovCloud Support: [support case URL]
```

### Incident Classification

| Severity | Definition | Response SLA | US-CERT Report |
|----------|------------|-------------|----------------|
| P1 — Critical | Active breach, data exfiltration, ransomware | 1 hour | Within 1 hour |
| P2 — High | Suspected breach, malware detected, admin account compromise | 4 hours | Within 24 hours |
| P3 — Medium | Unusual access patterns, WAF spike, failed MFA flood | 24 hours | Within 72 hours |
| P4 — Low | Policy violation, single failed login, config drift | 72 hours | Not required |

### Incident Response Phases

```
1. DETECTION
   - GuardDuty finding → EventBridge → SNS → PagerDuty/email
   - CloudWatch alarm → SNS
   - Manual report from user or 3PAO

2. CONTAINMENT (within SLA)
   - Isolate affected ECS tasks (update security group to deny all)
   - Revoke suspect user tokens (TokenDenylistService)
   - Disable AWS IAM credentials if compromised
   - Snapshot RDS for forensics before remediation

3. ERADICATION
   - Identify root cause (CloudTrail, VPC Flow Logs, audit_events)
   - Patch vulnerability or revoke compromised credential
   - Redeploy from clean image

4. RECOVERY
   - Restore from RDS snapshot if data was corrupted
   - Re-enable isolated services after validation
   - Monitor for 24 hours post-recovery

5. POST-INCIDENT
   - After-action report within 5 business days
   - Update POA&M with new finding
   - Brief AO within 72 hours of P1/P2 incidents
   - File US-CERT report (see timelines above)
```

### US-CERT Reporting (IR-6)

All P1 and P2 incidents must be reported to US-CERT via https://www.cisa.gov/report within the timeframes above. Required fields:
- Incident date/time
- System name and ATO authorization number
- Type of incident (from US-CERT taxonomy)
- Number of records potentially affected
- Actions taken

---

## 4. Contingency Plan (CP-2, CP-10)

### Recovery Objectives

| Metric | Target | Basis |
|--------|--------|-------|
| Recovery Time Objective (RTO) | 4 hours | FedRAMP Moderate typical |
| Recovery Point Objective (RPO) | 1 hour | RDS automated backup interval |

### Backup Schedule

| Resource | Method | Frequency | Retention |
|----------|--------|-----------|-----------|
| RDS PostgreSQL | Automated snapshots | Every 1 hour | 35 days |
| RDS PostgreSQL | Manual snapshot | Before every deployment | 90 days |
| S3 CV uploads | S3 versioning + cross-region replication | Continuous | 2 years |
| Redis (ElastiCache) | Redis AOF persistence | Continuous | Recovery only |
| Terraform state | S3 with versioning | On every apply | Indefinite |

### Recovery Runbook (abbreviated)

```
STEP 1 — Activate contingency
  [ ] Incident Commander declares contingency
  [ ] Notify AO and DevOps lead
  [ ] Open AWS Support case (GovCloud business support)

STEP 2 — Assess scope
  [ ] Is the database affected? → check RDS last successful backup
  [ ] Are ECS tasks running? → check ECS console
  [ ] Is VPC/networking intact? → check VPC Flow Logs and security groups

STEP 3 — Database recovery (if needed)
  [ ] List available snapshots:
      aws rds describe-db-snapshots --db-instance-identifier hrportal-db --region us-gov-west-1
  [ ] Restore from latest clean snapshot:
      aws rds restore-db-instance-from-db-snapshot \
        --db-instance-identifier hrportal-db-recovery \
        --db-snapshot-identifier <snapshot-id>
  [ ] Update Secrets Manager DB endpoint

STEP 4 — Application recovery
  [ ] Force new ECS deployment (pulls latest image from ECR)
  [ ] Verify health: curl https://<alb-dns>/actuator/health

STEP 5 — Validate
  [ ] Login + MFA flow works
  [ ] Audit events writing correctly
  [ ] No GuardDuty findings in last 30 minutes

STEP 6 — Post-recovery
  [ ] Document timeline and root cause
  [ ] Update AO within 2 hours
  [ ] Schedule post-incident review
```

---

## 5. Configuration Management Plan (CM-1, CM-2)

### CIS Baseline — Java Application

| CIS Control | Implementation |
|-------------|---------------|
| CIS Java 1.1 — TLS version | TLSv1.2 minimum enforced in `application.yml` |
| CIS Java 1.2 — Cipher suites | FIPS-approved suites only (see [01_FIPS_CRYPTO.md](01_FIPS_CRYPTO.md)) |
| CIS Java 2.1 — Security Manager | Not applicable (removed in Java 17+) |
| CIS Java 3.1 — Logging | SLF4J + MDC; no secrets in logs |

### CIS Baseline — Docker/Linux

| Control | Implementation |
|---------|---------------|
| Run as non-root | `USER spring` in Dockerfile |
| No SUID binaries | Verified via Trivy scan |
| Read-only filesystem | ECS task: `readonlyRootFilesystem: true` |
| Limit capabilities | ECS task: `dropCapabilities: [ALL]` |

### ECS Task Definition hardening

```json
{
  "containerDefinitions": [{
    "readonlyRootFilesystem": true,
    "user": "1000",
    "linuxParameters": {
      "capabilities": {
        "drop": ["ALL"],
        "add": []
      }
    },
    "mountPoints": [{
      "containerPath": "/tmp",
      "readOnly": false,
      "sourceVolume": "tmp-vol"
    }]
  }]
}
```

### Change Management Process

```
1. Developer creates feature branch
2. PR opened → security scan runs automatically (06_SECURITY_SCANNING.md)
3. PR review includes security checklist:
   [ ] No new hardcoded secrets
   [ ] Audit events added for new data-modifying operations
   [ ] Input validation on new API endpoints
   [ ] Dependencies pinned in pom.xml/package.json
4. Merge → staging deployment
5. DAST scan on staging
6. Production deployment requires Security Engineer sign-off for:
   - Infrastructure changes (Terraform)
   - Security configuration changes (SecurityConfig, JwtConfig)
   - New external integrations
```

---

## 6. Personnel Security (PS-4, PS-5)

### Termination Procedures (PS-4)

When an employee is terminated:

```
Day 0 (termination date):
  [ ] HR notifies IT via ServiceNow ticket
  [ ] Disable user account: PATCH /api/v1/users/{id}/deactivate
  [ ] Revoke all active sessions (Redis denylist all tokens for user)
  [ ] Disable AWS IAM user/role (if direct access granted)
  [ ] Remove from all GitHub teams
  [ ] Disable SSO/IdP account
  [ ] Recover hardware (laptop, PIV card, YubiKey)

Day 1:
  [ ] Audit review of last 30 days of activity for data exfiltration
  [ ] Change shared credentials if any (rotate API keys)

Day 90:
  [ ] Delete user account per retention policy
  [ ] Anonymize audit events referencing departed user
```

### Transfer Procedures (PS-5)

When an employee transfers to a different role/team:
- Review and adjust RBAC role/permissions within 24 hours
- Audit all permissions to confirm least-privilege after transfer
- Re-verify MFA enrollment on new role

---

## 7. Evidence Collection Schedule

For continuous ATO maintenance, collect and store evidence monthly:

| Evidence | Source | Storage Location |
|----------|--------|-----------------|
| Vulnerability scan report | Trivy JSON output | S3: `s3://hrportal-evidence/scans/YYYY-MM/` |
| Dependency check report | OWASP DC HTML | S3: `s3://hrportal-evidence/sca/YYYY-MM/` |
| AWS Config compliance snapshot | Config API | S3: `s3://hrportal-evidence/config/YYYY-MM/` |
| CloudTrail digest | CloudTrail S3 | `s3://hrportal-cloudtrail/` (auto-collected) |
| GuardDuty summary | EventBridge export | S3: `s3://hrportal-evidence/guardduty/YYYY-MM/` |
| Audit log export | `/api/v1/audit/export` | S3: `s3://hrportal-evidence/audit/YYYY-MM/` |
| POA&M | Manual | S3: `s3://hrportal-evidence/poam/YYYY-MM/` |
| System inventory | Terraform state | S3: `s3://hrportal-tfstate/` |

---

## 8. Access Policy Documents Required (AC-1 and other -1 controls)

Each control family requires a formal policy document. At minimum, the following must be written before ATO:

| Document | Controls Addressed | Owner |
|----------|------------------|-------|
| Access Control Policy | AC-1 | Security Engineer |
| Audit and Accountability Policy | AU-1 | Security Engineer |
| Identification and Authentication Policy | IA-1 | Security Engineer |
| System and Communications Protection Policy | SC-1 | Security Engineer |
| System and Information Integrity Policy | SI-1 | Security Engineer |
| Contingency Planning Policy | CP-1 | Security Engineer |
| Configuration Management Policy | CM-1 | DevOps Lead |
| Incident Response Policy | IR-1 | Security Engineer |
| Risk Assessment Policy | RA-1 | Security Engineer |
| System and Services Acquisition Policy | SA-1 | Product Lead |
| Personnel Security Policy | PS-1 | HR + Security |
| Physical and Environmental Protection Policy | PE-1 | Inherited from AWS (document in SSP) |

---

## 9. Acceptance Criteria

- [ ] Monthly ConMon report template populated and approved by AO
- [ ] POA&M initialized with all items from [00_OVERVIEW.md](00_OVERVIEW.md) priority matrix
- [ ] IRP reviewed and signed by Incident Commander and AO
- [ ] US-CERT reporting procedure tested (tabletop exercise)
- [ ] Contingency Plan recovery runbook tested (RDS restore test completed)
- [ ] Nightly RDS snapshot verified restorable (quarterly test)
- [ ] ECS task hardening (read-only root, drop ALL capabilities) deployed
- [ ] Evidence S3 bucket with Object Lock created
- [ ] All 12 policy documents drafted (stub acceptable for initial ATO)
- [ ] Termination checklist tested with off-boarding dry run

---

## 10. Related Documents

- [05_AUDIT_SIEM.md](05_AUDIT_SIEM.md) — Audit log exports are core ConMon evidence
- [06_SECURITY_SCANNING.md](06_SECURITY_SCANNING.md) — Monthly scan reports are ConMon deliverables
- [04_GOVCLOUD_MIGRATION.md](04_GOVCLOUD_MIGRATION.md) — CloudTrail in GovCloud is mandatory for ConMon
- [00_OVERVIEW.md](00_OVERVIEW.md) — Master control gap matrix and ATO roadmap
