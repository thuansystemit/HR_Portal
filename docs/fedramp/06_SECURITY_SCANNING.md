# 06 — CI Security Scanning (SAST / DAST / SCA / Malware)
> Controls: SI-2 (Gap), SI-3 (Gap), RA-5 (Gap)
> Priority: P1-3, P1-4
> Effort: M — 2–3 weeks

---

## Problem Statement

No security scanning exists in CI or at runtime:

| Gap | Impact | Control |
|-----|--------|---------|
| No SCA (dependency check) | Known CVEs in JAR dependencies go undetected | RA-5, SI-2 |
| No SAST | Source code vulnerabilities (injection, XXE, etc.) undetected | SI-2 |
| No container scanning | Vulnerable OS packages in Docker image | SI-2 |
| No DAST | Runtime vulnerabilities (OWASP Top 10) undetected in staging | RA-5 |
| No malware scan on CV uploads | Malicious file uploads pass through to storage and LLM | SI-3 |

---

## 1. Dependency Scanning — OWASP Dependency-Check (SCA)

### `pom.xml` plugin addition

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>9.1.0</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>      <!-- Fail on HIGH+ CVEs -->
        <suppressionFile>owasp-suppressions.xml</suppressionFile>
        <formats>HTML,JSON,SARIF</formats>
        <nvdApiKey>${env.NVD_API_KEY}</nvdApiKey> <!-- NVD API key for faster updates -->
    </configuration>
    <executions>
        <execution>
            <goals><goal>check</goal></goals>
        </execution>
    </executions>
</plugin>
```

Run manually: `mvn dependency-check:check`

### Suppression file template (`owasp-suppressions.xml`)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
    <!-- Document each suppression with: CVE ID, reason, and review date -->
    <!--
    <suppress>
        <notes>CVE-2023-XXXX: False positive — we do not use the affected code path.
               Reviewed: 2026-06-01. Re-review by: 2026-09-01.</notes>
        <cve>CVE-2023-XXXX</cve>
    </suppress>
    -->
</suppressions>
```

---

## 2. SAST — Semgrep

Semgrep provides Java, Spring, and OWASP rule sets with low false-positive rates.

### GitHub Actions step

```yaml
# .github/workflows/security.yml
- name: Run Semgrep SAST
  uses: semgrep/semgrep-action@v1
  with:
    config: >-
      p/java
      p/spring
      p/owasp-top-ten
      p/secrets
    generateSarif: true
  env:
    SEMGREP_APP_TOKEN: ${{ secrets.SEMGREP_APP_TOKEN }}

- name: Upload SARIF to GitHub Security
  uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: semgrep.sarif
  if: always()
```

**Key rules to enforce:**
- `java.lang.security.audit.sqli.*` — SQL injection
- `java.spring.security.audit.*` — Spring Security misconfigurations
- `generic.secrets.security.detected-*` — hardcoded secrets
- `java.lang.security.audit.xxe.*` — XML External Entity

---

## 3. Container Scanning — Trivy

Trivy scans Docker images for OS CVEs and misconfigurations.

```yaml
# .github/workflows/security.yml
- name: Build Docker image
  run: docker build -t hrportal-backend:${{ github.sha }} .

- name: Run Trivy container scan
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: hrportal-backend:${{ github.sha }}
    format: sarif
    output: trivy-results.sarif
    severity: CRITICAL,HIGH
    exit-code: 1         # Fail build on CRITICAL/HIGH
    ignore-unfixed: true # Skip if no patch available

- name: Upload Trivy SARIF
  uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: trivy-results.sarif
  if: always()
```

Also scan the base image (`eclipse-temurin:21-jre-alpine` or Azul Zulu) separately:

```yaml
- name: Scan base image
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: azul/zulu-openjdk:21-jre-latest
    severity: CRITICAL
    exit-code: 0    # Warning only for base image CVEs we can't control
```

---

## 4. Complete GitHub Actions Workflow

```yaml
# .github/workflows/security.yml
name: Security Scanning

on:
  push:
    branches: [main, master, release/**]
  pull_request:
  schedule:
    - cron: '0 6 * * 1'   # Weekly Monday 6 AM UTC

jobs:
  sca:
    name: Dependency Check (SCA)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
          cache: maven
      - name: OWASP Dependency Check
        working-directory: demo-app-backend
        run: mvn dependency-check:check -DnvdApiKey=${{ secrets.NVD_API_KEY }}
        env:
          NVD_API_KEY: ${{ secrets.NVD_API_KEY }}
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: dependency-check-report
          path: demo-app-backend/target/dependency-check-report.html

  sast:
    name: SAST (Semgrep)
    runs-on: ubuntu-latest
    container:
      image: semgrep/semgrep
    steps:
      - uses: actions/checkout@v4
      - run: semgrep --config p/java --config p/spring --config p/owasp-top-ten --sarif --output semgrep.sarif demo-app-backend/src
      - uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: semgrep.sarif
        if: always()

  container-scan:
    name: Container Scan (Trivy)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build image
        working-directory: demo-app-backend
        run: |
          docker run --rm -v "$(pwd)":/app -w /app maven:3.9-eclipse-temurin-21 mvn package -DskipTests -q
          docker build -t hrportal-backend:${{ github.sha }} .
      - uses: aquasecurity/trivy-action@master
        with:
          image-ref: hrportal-backend:${{ github.sha }}
          format: sarif
          output: trivy.sarif
          severity: CRITICAL,HIGH
          exit-code: 1
          ignore-unfixed: true
      - uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: trivy.sarif
        if: always()

  dast:
    name: DAST (OWASP ZAP)
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - name: Start staging stack
        run: docker compose -f docker-compose.staging.yml up -d
      - name: Wait for app
        run: |
          until curl -sf http://localhost:8080/actuator/health; do sleep 5; done
      - name: ZAP Baseline Scan
        uses: zaproxy/action-baseline@v0.12.0
        with:
          target: http://localhost:8080
          rules_file_name: zap-rules.tsv
          fail_action: true
          artifact_name: zap-report
      - name: Tear down
        if: always()
        run: docker compose -f docker-compose.staging.yml down -v
```

---

## 5. Malware Scanning on CV Uploads (SI-3)

CV files (PDF, DOCX, etc.) are uploaded to S3 and processed by the LLM. A malicious file could:
- Execute code in a vulnerable PDF parser
- Contain macros targeting the LLM preprocessing pipeline
- Be used as a storage/exfiltration vector

### Architecture

```
CV Upload → S3 (unscanned prefix) → Lambda (ClamAV scan) → 
  CLEAN → move to s3://hrportal-cv-uploads/clean/
  INFECTED → move to s3://hrportal-cv-quarantine/ + alert + audit event
```

### Option A — AWS GuardDuty Malware Protection (Recommended)

GuardDuty Malware Protection for S3 scans objects on upload automatically with no additional infrastructure.

```hcl
# terraform/guardduty.tf
resource "aws_guardduty_detector" "main" {
  enable = true

  datasources {
    malware_protection {
      scan_ec2_instance_with_findings { ebs_volumes { auto_enable_malware_scan = true } }
      s3_logs { enable = true }
    }
  }
}
```

When GuardDuty finds malware, it generates a finding in Security Hub and emits an EventBridge event.

### Option B — ClamAV Lambda (self-managed, for GovCloud where GuardDuty Malware Protection may not be available)

```python
# lambda/scan_cv.py
import boto3
import subprocess
import os

s3 = boto3.client('s3')

def handler(event, context):
    bucket = event['Records'][0]['s3']['bucket']['name']
    key    = event['Records'][0]['s3']['object']['key']

    # Download file
    tmp_path = f'/tmp/{os.path.basename(key)}'
    s3.download_file(bucket, key, tmp_path)

    # Run ClamAV scan
    result = subprocess.run(['clamscan', '--no-summary', tmp_path], capture_output=True)

    if result.returncode == 0:
        # Clean — move to processed prefix
        clean_key = key.replace('incoming/', 'clean/')
        s3.copy_object(Bucket=bucket, CopySource={'Bucket': bucket, 'Key': key}, Key=clean_key)
        s3.delete_object(Bucket=bucket, Key=key)
    else:
        # Infected — quarantine
        quarantine_key = key.replace('incoming/', 'quarantine/')
        s3.copy_object(Bucket='hrportal-quarantine', CopySource={'Bucket': bucket, 'Key': key}, Key=quarantine_key)
        s3.delete_object(Bucket=bucket, Key=key)
        # Publish alert to SNS
        sns = boto3.client('sns')
        sns.publish(TopicArn=os.environ['ALERT_TOPIC_ARN'],
                    Message=f'Malware detected in CV upload: {key}',
                    Subject='[ALERT] Malware detected in HR Portal CV upload')
```

### Backend — wait for clean status before processing

```java
// CvUploadService.java — poll for scan completion before dispatching to LLM
public void processUploadedCv(UUID candidateId, String s3Key) {
    // Wait for scan to complete (S3 object tag set by Lambda)
    int maxRetries = 10;
    for (int i = 0; i < maxRetries; i++) {
        Map<String, String> tags = getObjectTags(s3Key);
        String scanStatus = tags.get("scan-status");
        if ("CLEAN".equals(scanStatus)) {
            dispatchToLlm(candidateId, s3Key);
            return;
        } else if ("INFECTED".equals(scanStatus)) {
            auditService.log(null, "CV_MALWARE_DETECTED", "Candidate", candidateId, null,
                Map.of("s3Key", s3Key), "blocked");
            throw new MalwareDetectedException("Uploaded file failed malware scan");
        }
        Thread.sleep(2_000);
    }
    throw new ScanTimeoutException("CV malware scan timed out");
}
```

---

## 6. Dependabot Configuration

```yaml
# .github/dependabot.yml
version: 2
updates:
  - package-ecosystem: maven
    directory: /demo-app-backend
    schedule:
      interval: weekly
    open-pull-requests-limit: 10
    security-updates-only: false

  - package-ecosystem: npm
    directory: /demo-app
    schedule:
      interval: weekly
    open-pull-requests-limit: 10

  - package-ecosystem: docker
    directory: /demo-app-backend
    schedule:
      interval: weekly

  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
```

---

## 7. Acceptance Criteria

- [ ] `mvn dependency-check:check` fails build on any CVSS 7+ CVE
- [ ] Semgrep SAST runs on every PR and pushes results to GitHub Security tab
- [ ] Trivy scan fails build on CRITICAL container CVEs
- [ ] ZAP baseline scan runs on `main` branch deployments
- [ ] CV upload with EICAR test file is blocked and quarantined
- [ ] Audit event `CV_MALWARE_DETECTED` is written when malware is found
- [ ] Security team notified (SNS/email) within 5 minutes of malware detection
- [ ] Dependabot PRs open weekly for outdated dependencies
- [ ] All scan results visible in GitHub Security → Code Scanning tab

---

## 8. Related Documents

- [05_AUDIT_SIEM.md](05_AUDIT_SIEM.md) — Security scan findings feed into SIEM
- [04_GOVCLOUD_MIGRATION.md](04_GOVCLOUD_MIGRATION.md) — GuardDuty Malware Protection requires GovCloud
- [09_NETWORK_VPC.md](09_NETWORK_VPC.md) — WAF provides additional runtime protection complementing DAST
