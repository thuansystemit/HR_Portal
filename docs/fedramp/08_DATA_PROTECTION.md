# 08 — CUI/PII Data Protection
> Controls: SC-28 (Partial), AC-4 (Partial — ui-avatars.com), SA-9 (Gap — external LLM)
> Priority: P1-7, P2-6, P2-7
> Effort: M — 2–3 weeks (P1-7), M — 2–3 weeks (P2-6), L — 4–6 weeks (P2-7)

---

## Problem Statement

| Gap | Control | Priority |
|-----|---------|----------|
| `ui-avatars.com` sends user name (PII) to external service | AC-4 | P1-7 |
| No field-level encryption on `cv_candidates` PII columns | SC-28 | P2-6 |
| External LLM (OpenAI/Anthropic) processes candidate PII | SA-9 | P2-7 |

---

## 1. Remove ui-avatars.com (P1-7 / AC-4)

### Problem

`header.ts` calls `https://ui-avatars.com/api/?name=<encoded-name>&...`. This sends the user's name to a third-party server on every page load, violating AC-4 (information flow enforcement) and potentially GDPR/Privacy Act requirements.

### Fix A — Server-generated SVG avatar (recommended)

Generate a simple initials-based SVG avatar server-side. No external dependency, no PII egress.

```java
// AvatarController.java
@RestController
@RequestMapping("/api/v1/users")
public class AvatarController {

    @GetMapping(value = "/avatar", produces = "image/svg+xml")
    public ResponseEntity<String> getAvatar(
            @RequestParam String initials,
            @RequestParam(defaultValue = "0d6efd") String bg) {

        // Sanitize input — initials only, 1-2 chars, A-Z
        String safe = initials.replaceAll("[^A-Z]", "").toUpperCase();
        if (safe.isBlank() || safe.length() > 2) safe = "?";
        String safeBg = bg.replaceAll("[^a-fA-F0-9]", "").substring(0, Math.min(6, bg.length()));

        String svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32">
              <rect width="32" height="32" rx="16" fill="#%s"/>
              <text x="16" y="21" text-anchor="middle" font-family="sans-serif"
                    font-size="13" font-weight="600" fill="#ffffff">%s</text>
            </svg>
            """.formatted(safeBg, safe);

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
            .body(svg);
    }
}
```

### Fix B — Local Angular SVG component (zero network call)

```typescript
// avatar.component.ts
@Component({
  selector: 'app-avatar',
  standalone: true,
  template: `
    <svg [attr.width]="size" [attr.height]="size" [attr.viewBox]="'0 0 ' + size + ' ' + size"
         xmlns="http://www.w3.org/2000/svg">
      <rect [attr.width]="size" [attr.height]="size" [attr.rx]="size/2" [attr.fill]="'#' + bg"/>
      <text [attr.x]="size/2" [attr.y]="size * 0.66"
            text-anchor="middle" font-family="sans-serif"
            [attr.font-size]="size * 0.4" font-weight="600" fill="#ffffff">{{ initials() }}</text>
    </svg>
  `
})
export class AvatarComponent {
  @Input() name = '';
  @Input() size = 32;
  @Input() bg   = '0d6efd';
  protected initials = computed(() =>
    this.name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase() || '?'
  );
}
```

Update `header.ts`:

```typescript
// Remove avatarUrl computed signal entirely
// Replace <img [src]="avatarUrl()"> with:
// <app-avatar [name]="auth.user()?.name ?? ''" [size]="30"></app-avatar>
```

---

## 2. Field-Level Encryption for `cv_candidates` PII (P2-6 / SC-28)

AWS KMS storage encryption (RDS encrypted at rest) protects the physical disk. However, if the DB is queried directly (e.g., by a DBA, auditor, or compromised app user), PII columns are readable in cleartext. Field-level encryption adds an additional layer.

### PII columns to encrypt in `cv_candidates`

| Column | Sensitivity |
|--------|------------|
| `email` | PII |
| `phone` | PII |
| `full_name` | PII |
| `address` | PII |
| `ssn_last4` | PII (if present) |
| `cv_s3_key` | Indirect PII (points to CV document) |

### JPA `@Converter` approach

Encryption/decryption is handled transparently in the JPA converter — no change to service or controller code.

```java
package com.demo.app.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.*;
import java.util.Base64;

@Converter
@Component
@RequiredArgsConstructor
public class PiiEncryptionConverter implements AttributeConverter<String, String> {

    private final KmsClient kmsClient;
    private final String dataKeyId;   // injected from @Value("${app.kms.data-key-id}")

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) return null;
        EncryptResponse resp = kmsClient.encrypt(EncryptRequest.builder()
            .keyId(dataKeyId)
            .plaintext(SdkBytes.fromUtf8String(plaintext))
            .encryptionAlgorithm(EncryptionAlgorithmSpec.RSAES_OAEP_SHA_256)
            .build());
        return Base64.getEncoder().encodeToString(resp.ciphertextBlob().asByteArray());
    }

    @Override
    public String convertToEntityAttribute(String ciphertext) {
        if (ciphertext == null) return null;
        DecryptResponse resp = kmsClient.decrypt(DecryptRequest.builder()
            .ciphertextBlob(SdkBytes.fromBase64String(ciphertext))
            .encryptionAlgorithm(EncryptionAlgorithmSpec.RSAES_OAEP_SHA_256)
            .build());
        return resp.plaintext().asUtf8String();
    }
}
```

### Apply to entity

```java
// CvCandidate.java
@Convert(converter = PiiEncryptionConverter.class)
@Column(name = "email")
private String email;

@Convert(converter = PiiEncryptionConverter.class)
@Column(name = "full_name")
private String fullName;

@Convert(converter = PiiEncryptionConverter.class)
@Column(name = "phone")
private String phone;
```

### Performance considerations

KMS API calls add latency (~1-5ms per field, per row). For bulk operations:
- Use KMS **data key** (AES-256 envelope encryption) for high-throughput scenarios
- Cache the plaintext data key in memory for ≤5 minutes (AWS best practice)

```java
// Envelope encryption — faster for bulk
// 1. GenerateDataKey → { plaintext AES key, encrypted AES key }
// 2. Encrypt data locally with AES key
// 3. Store { encrypted_data_key || IV || ciphertext } in DB
// 4. On decrypt: call KMS Decrypt on the encrypted_data_key, then decrypt locally
```

### Migration — re-encrypt existing rows

```java
// One-time migration job (run once, then remove)
@Component
public class PiiEncryptionMigrationJob implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        if (!migrationEnabled) return;
        candidateRepository.findAllUnencrypted().forEach(candidate -> {
            // Triggers converter on save
            candidateRepository.save(candidate);
        });
    }
}
```

---

## 3. Self-Hosted LLM — Eliminate External PII Egress (P2-7 / SA-9)

### Problem

If the CV batch extractor sends candidate CVs to OpenAI, Anthropic, or any external LLM API, candidate PII leaves the GovCloud boundary. This violates SA-9 (external information system services) and is incompatible with FedRAMP.

### Required architecture

```
CV Upload (S3 GovCloud)
    │
    ▼
cv-batch-extractor (ECS task, GovCloud)
    │
    ▼ (VPC-internal traffic only)
Self-Hosted LLM (AWS Bedrock GovCloud OR Ollama on EC2)
    │
    ▼
Structured JSON → demo-app-backend (ECS, GovCloud)
```

### Option A — AWS Bedrock (GovCloud)

AWS Bedrock is available in GovCloud and holds FedRAMP authorization. Supported models include Amazon Titan, Claude (Anthropic), and Llama 2.

```python
# cv-batch-extractor — update llm_service.py
import boto3

bedrock = boto3.client('bedrock-runtime', region_name='us-gov-west-1',
                        endpoint_url='https://bedrock-runtime.us-gov-west-1.amazonaws.com')

def extract_cv(cv_text: str) -> dict:
    response = bedrock.invoke_model(
        modelId='amazon.titan-text-express-v1',   # or 'anthropic.claude-v2' if available
        body=json.dumps({
            'inputText': f'{CV_PROMPT}\n\n{cv_text}',
            'textGenerationConfig': { 'maxTokenCount': 2048, 'temperature': 0.1 }
        }),
        contentType='application/json',
        accept='application/json'
    )
    return json.loads(response['body'].read())
```

### Option B — Ollama on EC2 GovCloud (fully air-gapped)

For maximum isolation, run a quantized LLM (Llama 3, Mistral) on a dedicated EC2 instance in the private subnet. No internet egress required.

```yaml
# docker-compose for Ollama EC2 instance
services:
  ollama:
    image: ollama/ollama:latest
    volumes:
      - ollama_data:/root/.ollama
    ports:
      - "11434:11434"
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
```

```python
# cv-batch-extractor — update llm_service.py for Ollama
import httpx

OLLAMA_URL = os.getenv("OLLAMA_URL", "http://ollama.internal:11434")

def extract_cv(cv_text: str) -> dict:
    resp = httpx.post(f"{OLLAMA_URL}/api/generate", json={
        "model": "llama3:8b-instruct",
        "prompt": f"{CV_PROMPT}\n\n{cv_text}",
        "stream": False,
        "format": "json"
    }, timeout=60.0)
    return resp.json()['response']
```

### Data handling policy

Even with self-hosted LLM:
- CV text should be stripped of SSN, DOB before LLM processing (PII minimization)
- LLM logs must not persist raw PII input
- Extracted structured data (name, skills, experience) is the only output stored

---

## 4. Data Retention Policy

| Data Type | Retention | Deletion Method |
|-----------|-----------|-----------------|
| CV files (S3) | 2 years from upload | S3 lifecycle rule → Glacier → delete |
| `cv_candidates` PII | 2 years from last activity | Scheduled job → anonymize |
| `audit_events` | 3 years (FedRAMP AU-11) | S3 Object Lock; no deletion |
| User accounts (inactive) | 90 days after termination | Automated disable + 90-day delete |
| Application logs | 1 year | CloudWatch log group retention |

```sql
-- Flyway migration: V25__add_retention_fields.sql
ALTER TABLE cv_candidates
    ADD COLUMN retention_expires_at TIMESTAMPTZ GENERATED ALWAYS AS
        (created_at + INTERVAL '2 years') STORED;
ALTER TABLE users
    ADD COLUMN scheduled_deletion_at TIMESTAMPTZ;  -- set on deactivation
```

```hcl
# terraform/s3.tf — lifecycle for CV uploads
resource "aws_s3_bucket_lifecycle_configuration" "cv_uploads" {
  bucket = aws_s3_bucket.cv_uploads.id
  rule {
    id     = "cv-retention"
    status = "Enabled"
    transition {
      days          = 365
      storage_class = "GLACIER"
    }
    expiration {
      days = 730   # 2 years
    }
  }
}
```

---

## 5. Acceptance Criteria

- [ ] No HTTP request to `ui-avatars.com` appears in browser network tab
- [ ] Avatar displays correctly using server-generated SVG or local Angular component
- [ ] `cv_candidates.email` stored as Base64-encoded ciphertext in DB
- [ ] `cv_candidates.full_name` stored as ciphertext, readable after JPA conversion
- [ ] `SELECT email FROM cv_candidates LIMIT 1` returns ciphertext (not plaintext) when executed by DB user
- [ ] LLM API calls stay within VPC (no egress to `api.openai.com` or `api.anthropic.com`)
- [ ] CV S3 lifecycle rule transitions to Glacier at 365 days, deletes at 730 days
- [ ] Data retention policy documented in SSP Appendix

---

## 6. Related Documents

- [01_FIPS_CRYPTO.md](01_FIPS_CRYPTO.md) — KMS key for PII field encryption
- [04_GOVCLOUD_MIGRATION.md](04_GOVCLOUD_MIGRATION.md) — S3 and Bedrock must be in GovCloud
- [09_NETWORK_VPC.md](09_NETWORK_VPC.md) — VPC endpoint for Bedrock; no internet egress
