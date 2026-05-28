# 04 — AWS GovCloud Migration
> Controls: PE (inherited), all SC/AC/AU inherited controls
> Priority: P0-5
> Effort: XL — 8–12 weeks

---

## Problem Statement

The system currently runs on commercial AWS (`us-east-1` or similar). **Commercial AWS does not inherit FedRAMP authorization for Moderate workloads.** Processing federal PII and employment records on commercial AWS cannot be authorized under FedRAMP Moderate.

AWS GovCloud (US) (`us-gov-west-1`, `us-gov-east-1`) holds a FedRAMP High P-ATO, covering all inherited controls (PE, CP inherited from AWS physical/environmental). This migration is a hard ATO blocker.

---

## 1. GovCloud vs Commercial AWS — Key Differences

| Aspect | Commercial AWS | GovCloud |
|--------|---------------|----------|
| FedRAMP authorization | Not authorized for Moderate | FedRAMP High P-ATO |
| Account type | Standard | Separate account; requires US persons |
| Regions | `us-east-1`, `us-west-2`, etc. | `us-gov-west-1`, `us-gov-east-1` only |
| Service availability | All services | ~80% of services (see below) |
| KMS | Standard | GovCloud KMS with FIPS endpoints |
| ECR | Available | Available |
| RDS | Available | Available |
| ElastiCache | Available | Available |
| CloudFront | Available | Available (limited PoPs) |
| S3 | Available | Available (FIPS endpoint required) |
| EKS | Available | Available |
| Secrets Manager | Available | Available |
| ACM | Available | Available |
| login.gov | — | Required (only callable from GovCloud) |

---

## 2. FIPS Endpoints

All AWS SDK calls **must** use FIPS-validated endpoints in GovCloud. Configure via environment variables or SDK config:

```bash
# Environment variables for ECS task definition
AWS_USE_FIPS_ENDPOINT=true
AWS_DEFAULT_REGION=us-gov-west-1
```

| Service | FIPS Endpoint |
|---------|--------------|
| KMS | `https://kms-fips.us-gov-west-1.amazonaws.com` |
| S3 | `https://s3-fips.us-gov-west-1.amazonaws.com` |
| Secrets Manager | `https://secretsmanager.us-gov-west-1.amazonaws.com` |
| STS | `https://sts.us-gov-west-1.amazonaws.com` |

---

## 3. Infrastructure Migration Plan

### Phase 1 — GovCloud Account Setup (Weeks 1–2)

```
1. Create AWS GovCloud commercial root account (linked to standard AWS account)
2. Enable AWS Organizations in GovCloud
3. Create member accounts:
   - gov-prod  (production workloads)
   - gov-stage (staging/UAT)
   - gov-dev   (developer sandbox)
4. Enable AWS Config, CloudTrail, GuardDuty in all accounts
5. Set up IAM Identity Center (SSO) for GovCloud
```

### Phase 2 — Terraform Migration (Weeks 2–6)

Update `provider` block in all Terraform modules:

```hcl
# terraform/providers.tf
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "us-gov-west-1"

  # FIPS endpoints
  endpoints {
    kms            = "https://kms-fips.us-gov-west-1.amazonaws.com"
    s3             = "https://s3-fips.us-gov-west-1.amazonaws.com"
    secretsmanager = "https://secretsmanager.us-gov-west-1.amazonaws.com"
    sts            = "https://sts.us-gov-west-1.amazonaws.com"
  }

  default_tags {
    tags = {
      Environment = "production"
      FedRAMP     = "moderate"
      DataClass   = "CUI"
    }
  }
}
```

### Phase 3 — Data Migration (Weeks 6–10)

```
1. Create RDS PostgreSQL in GovCloud (same version as current)
2. Enable RDS encryption at rest with GovCloud KMS key
3. pg_dump from commercial → encrypt → transfer via AWS DataSync or S3 cross-account
4. Restore to GovCloud RDS
5. Verify data integrity (row counts, spot checks)
6. Parallel run: route a % of traffic to GovCloud stack
7. DNS cutover
8. Decommission commercial resources (30-day retention period)
```

**Data transfer — never send plaintext PII across public internet:**

```bash
# On commercial side
pg_dump -h $COMMERCIAL_DB_HOST -U $DB_USER hrportal_db | \
  aws kms encrypt --key-id $GOVCLOUD_KMS_KEY_ID --plaintext fileb:// | \
  aws s3 cp - s3://hrportal-migration-encrypted/dump.enc

# On GovCloud side
aws s3 cp s3://hrportal-migration-encrypted/dump.enc - | \
  aws kms decrypt --ciphertext-blob fileb:// --output text --query Plaintext | \
  base64 -d | psql -h $GOVCLOUD_DB_HOST -U $DB_USER hrportal_db
```

---

## 4. Docker / ECS Task Definition Updates

### ECS Task Definition

```json
{
  "family": "hrportal-backend",
  "executionRoleArn": "arn:aws-us-gov:iam::ACCOUNT_ID:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws-us-gov:iam::ACCOUNT_ID:role/hrportal-task-role",
  "requiresCompatibilities": ["FARGATE"],
  "networkMode": "awsvpc",
  "cpu": "1024",
  "memory": "2048",
  "containerDefinitions": [
    {
      "name": "backend",
      "image": "ACCOUNT_ID.dkr.ecr.us-gov-west-1.amazonaws.com/hrportal-backend:latest",
      "environment": [
        { "name": "AWS_USE_FIPS_ENDPOINT", "value": "true" },
        { "name": "AWS_DEFAULT_REGION",    "value": "us-gov-west-1" }
      ],
      "secrets": [
        { "name": "SPRING_DATASOURCE_URL",      "valueFrom": "arn:aws-us-gov:secretsmanager:us-gov-west-1:...:secret:hrportal/db-url" },
        { "name": "SPRING_DATASOURCE_USERNAME", "valueFrom": "arn:aws-us-gov:secretsmanager:us-gov-west-1:...:secret:hrportal/db-user" },
        { "name": "SPRING_DATASOURCE_PASSWORD", "valueFrom": "arn:aws-us-gov:secretsmanager:us-gov-west-1:...:secret:hrportal/db-pass" },
        { "name": "JWT_KMS_KEY_ID",             "valueFrom": "arn:aws-us-gov:secretsmanager:us-gov-west-1:...:secret:hrportal/jwt-kms-key-id" }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/hrportal-backend",
          "awslogs-region": "us-gov-west-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

### ECR in GovCloud

```bash
# Authenticate to GovCloud ECR
aws ecr get-login-password --region us-gov-west-1 | \
  docker login --username AWS --password-stdin \
  ACCOUNT_ID.dkr.ecr.us-gov-west-1.amazonaws.com

# Tag and push
docker tag hrportal-backend:latest \
  ACCOUNT_ID.dkr.ecr.us-gov-west-1.amazonaws.com/hrportal-backend:latest
docker push ACCOUNT_ID.dkr.ecr.us-gov-west-1.amazonaws.com/hrportal-backend:latest
```

---

## 5. Secrets Manager Migration

Remove all secrets from `.env` files, `application.yml` plaintext, and `docker-compose.yml`:

| Secret | Commercial | GovCloud Target |
|--------|-----------|-----------------|
| DB password | `docker-compose.yml` env var | Secrets Manager |
| JWT signing key | In-memory (JwtConfig) | KMS asymmetric key |
| Internal API key | `application.yml` default | Secrets Manager |
| Redis password | `docker-compose.yml` env var | Secrets Manager |
| LLM API key | `.env` file | Secrets Manager |

**Spring Cloud AWS Secrets Manager integration:**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-starter-secrets-manager</artifactId>
    <version>3.1.1</version>
</dependency>
```

```yaml
# application.yml
spring:
  cloud:
    aws:
      secretsmanager:
        enabled: true
        region: us-gov-west-1
      credentials:
        instance-profile: true   # ECS task role
```

---

## 6. S3 Upload Migration

CV upload bucket must be in GovCloud with server-side encryption using KMS:

```hcl
# terraform/s3.tf
resource "aws_s3_bucket" "cv_uploads" {
  bucket = "hrportal-cv-uploads-${var.account_id}"

  tags = {
    DataClass = "CUI"
    FedRAMP   = "moderate"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "cv_uploads" {
  bucket = aws_s3_bucket.cv_uploads.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.data_encryption.arn
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "cv_uploads" {
  bucket                  = aws_s3_bucket.cv_uploads.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
```

---

## 7. Application Configuration Changes

### Remove external avatar service (P1-7 / AC-4)

The `ui-avatars.com` call in `header.ts` creates PII egress to a third-party service. In GovCloud this must be eliminated:

```typescript
// header.ts — replace avatarUrl computed signal
protected avatarUrl = computed(() => {
  // Generate initials-based avatar locally, no external call
  const name = this.auth.user()?.name ?? 'U';
  const initials = name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase();
  return `/api/v1/users/avatar?initials=${initials}`;
});
```

Or use a self-hosted avatar library (DiceBear via Angular component, no external HTTP call).

---

## 8. Pre-Migration Checklist

- [ ] GovCloud accounts created and linked to commercial
- [ ] IAM Identity Center configured for GovCloud
- [ ] VPC architecture deployed (see [09_NETWORK_VPC.md](09_NETWORK_VPC.md))
- [ ] RDS PostgreSQL provisioned with KMS encryption
- [ ] ElastiCache Redis provisioned (in-transit + at-rest encryption)
- [ ] ECR repositories created in `us-gov-west-1`
- [ ] KMS keys created: data encryption, JWT signing, S3 encryption
- [ ] Secrets Manager secrets populated
- [ ] ECS cluster and task definitions updated
- [ ] CloudTrail enabled in all GovCloud accounts
- [ ] GuardDuty enabled
- [ ] AWS Config rules deployed

---

## 9. Acceptance Criteria

- [ ] All application traffic flows through `us-gov-west-1` or `us-gov-east-1`
- [ ] No AWS API calls to commercial endpoints
- [ ] All secrets sourced from GovCloud Secrets Manager (no plaintext in env or config)
- [ ] RDS encryption verified: `aws rds describe-db-instances | grep StorageEncrypted` → `true`
- [ ] S3 bucket policy denies non-FIPS endpoint access
- [ ] `ui-avatars.com` domain does not appear in network traffic
- [ ] CloudTrail capturing all API calls in GovCloud account
- [ ] All ECS task logs flowing to CloudWatch in `us-gov-west-1`

---

## 10. Related Documents

- [01_FIPS_CRYPTO.md](01_FIPS_CRYPTO.md) — KMS key creation must happen in GovCloud
- [09_NETWORK_VPC.md](09_NETWORK_VPC.md) — VPC architecture for GovCloud
- [05_AUDIT_SIEM.md](05_AUDIT_SIEM.md) — CloudTrail + SIEM integration for GovCloud
- [08_DATA_PROTECTION.md](08_DATA_PROTECTION.md) — S3 encryption, LLM self-hosting
