# 09 — GovCloud VPC Architecture + GuardDuty + WAF
> Controls: SC-5 (Partial), SC-7 (Partial), AC-17 (Partial), P1-8 (unsafe-inline CSP)
> Priority: P1/P2
> Effort: M — 2–3 weeks

---

## Problem Statement

| Gap | Control |
|-----|---------|
| No VPC segmentation — all services on flat network | SC-7 |
| No WAF — only Nginx rate limit | SC-5 |
| No GuardDuty security findings | SC-7 |
| TLS only for remote access — no VPN/jump box | AC-17 |
| `unsafe-inline` in Content-Security-Policy | SC-7 |
| No VPC Flow Logs | SC-7 / AU-2 |

---

## 1. VPC Architecture — 3-Tier Design

```
┌─────────────────────────────────────────────────────┐
│ VPC: 10.0.0.0/16  (us-gov-west-1)                  │
│                                                      │
│  ┌────────────────────────┐                          │
│  │  Public Subnets        │  10.0.0.0/24 (AZ-a)     │
│  │                        │  10.0.1.0/24 (AZ-b)     │
│  │  • ALB (HTTPS only)    │                          │
│  │  • NAT Gateway         │                          │
│  └────────────┬───────────┘                          │
│               │ (SG: only 443 inbound from ALB)      │
│  ┌────────────▼───────────┐                          │
│  │  Private App Subnets   │  10.0.10.0/24 (AZ-a)    │
│  │                        │  10.0.11.0/24 (AZ-b)    │
│  │  • ECS Fargate tasks   │                          │
│  │    (backend, frontend) │                          │
│  │  • Ollama EC2 (LLM)    │                          │
│  └────────────┬───────────┘                          │
│               │ (SG: only 5432/6379 from app tier)   │
│  ┌────────────▼───────────┐                          │
│  │  Private Data Subnets  │  10.0.20.0/24 (AZ-a)    │
│  │                        │  10.0.21.0/24 (AZ-b)    │
│  │  • RDS PostgreSQL      │                          │
│  │  • ElastiCache Redis   │                          │
│  └────────────────────────┘                          │
└─────────────────────────────────────────────────────┘
```

### Terraform VPC module

```hcl
# terraform/vpc.tf
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "5.5.3"

  name = "hrportal-govcloud"
  cidr = "10.0.0.0/16"

  azs              = ["us-gov-west-1a", "us-gov-west-1b"]
  public_subnets   = ["10.0.0.0/24",  "10.0.1.0/24"]
  private_subnets  = ["10.0.10.0/24", "10.0.11.0/24"]
  database_subnets = ["10.0.20.0/24", "10.0.21.0/24"]

  enable_nat_gateway     = true
  single_nat_gateway     = false   # HA — one per AZ
  enable_vpn_gateway     = false   # No site-to-site VPN needed initially
  enable_dns_hostnames   = true
  enable_dns_support     = true

  enable_flow_log                      = true
  flow_log_destination_type            = "cloud-watch-logs"
  flow_log_cloudwatch_log_group_name   = "/aws/vpc/hrportal-flow-logs"
  flow_log_cloudwatch_iam_role_arn     = aws_iam_role.vpc_flow_logs.arn

  tags = {
    FedRAMP = "moderate"
  }
}
```

---

## 2. Security Groups

### ALB Security Group

```hcl
resource "aws_security_group" "alb" {
  name   = "hrportal-alb"
  vpc_id = module.vpc.vpc_id

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTPS from internet"
  }

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTP — redirect to HTTPS only"
  }

  egress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
    description     = "ALB to backend tasks"
  }
}
```

### App Tier Security Group

```hcl
resource "aws_security_group" "app" {
  name   = "hrportal-app"
  vpc_id = module.vpc.vpc_id

  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
    description     = "Only from ALB"
  }

  egress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.data.id]
    description     = "PostgreSQL"
  }

  egress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.data.id]
    description     = "Redis"
  }

  egress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTPS egress for AWS API calls (via NAT)"
  }
}
```

### Data Tier Security Group

```hcl
resource "aws_security_group" "data" {
  name   = "hrportal-data"
  vpc_id = module.vpc.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
    description     = "PostgreSQL from app tier only"
  }

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
    description     = "Redis from app tier only"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = []   # No egress from data tier
  }
}
```

---

## 3. VPC Endpoints (Eliminate Internet Egress for AWS APIs)

Replace NAT Gateway traffic for AWS services with VPC Interface Endpoints:

```hcl
# terraform/vpc_endpoints.tf
locals {
  vpc_endpoint_services = [
    "com.amazonaws.us-gov-west-1.kms-fips",
    "com.amazonaws.us-gov-west-1.secretsmanager",
    "com.amazonaws.us-gov-west-1.s3",
    "com.amazonaws.us-gov-west-1.ecr.api",
    "com.amazonaws.us-gov-west-1.ecr.dkr",
    "com.amazonaws.us-gov-west-1.logs",
    "com.amazonaws.us-gov-west-1.bedrock-runtime",
    "com.amazonaws.us-gov-west-1.events",   # EventBridge
  ]
}

resource "aws_vpc_endpoint" "aws_services" {
  for_each = toset(local.vpc_endpoint_services)

  vpc_id              = module.vpc.vpc_id
  service_name        = each.value
  vpc_endpoint_type   = "Interface"
  subnet_ids          = module.vpc.private_subnets
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true

  tags = { Name = "hrportal-${replace(each.value, ".", "-")}" }
}

resource "aws_vpc_endpoint" "s3_gateway" {
  vpc_id            = module.vpc.vpc_id
  service_name      = "com.amazonaws.us-gov-west-1.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = module.vpc.private_route_table_ids
}
```

---

## 4. AWS WAF (SC-5)

```hcl
# terraform/waf.tf
resource "aws_wafv2_web_acl" "hrportal" {
  name  = "hrportal-waf"
  scope = "REGIONAL"

  default_action { allow {} }

  # AWS Managed Rules — Common
  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 10
    override_action { none {} }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "CommonRuleSet"
      sampled_requests_enabled   = true
    }
  }

  # AWS Managed Rules — Known Bad Inputs
  rule {
    name     = "AWSManagedRulesKnownBadInputsRuleSet"
    priority = 20
    override_action { none {} }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "KnownBadInputs"
      sampled_requests_enabled   = true
    }
  }

  # Rate limiting — 1000 requests per 5 min per IP
  rule {
    name     = "RateLimitPerIP"
    priority = 1
    action { block {} }
    statement {
      rate_based_statement {
        limit              = 1000
        aggregate_key_type = "IP"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "RateLimit"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "hrportal-waf"
    sampled_requests_enabled   = true
  }
}

resource "aws_wafv2_web_acl_association" "alb" {
  resource_arn = aws_lb.hrportal.arn
  web_acl_arn  = aws_wafv2_web_acl.hrportal.arn
}
```

---

## 5. GuardDuty

```hcl
# terraform/guardduty.tf
resource "aws_guardduty_detector" "main" {
  enable = true

  datasources {
    s3_logs             { enable = true }
    kubernetes          { audit_logs { enable = true } }
    malware_protection  {
      scan_ec2_instance_with_findings {
        ebs_volumes { auto_enable_malware_scan = true }
      }
    }
  }
}

# Export GuardDuty findings to EventBridge → SNS → Security team
resource "aws_cloudwatch_event_rule" "guardduty_findings" {
  name = "hrportal-guardduty-findings"
  event_pattern = jsonencode({
    source      = ["aws.guardduty"]
    detail-type = ["GuardDuty Finding"]
    detail      = { severity = [{ numeric = [">=", 7] }] }  # HIGH/CRITICAL only
  })
}

resource "aws_cloudwatch_event_target" "security_sns" {
  rule      = aws_cloudwatch_event_rule.guardduty_findings.name
  target_id = "SecuritySNS"
  arn       = aws_sns_topic.security_alerts.arn
}
```

---

## 6. Content-Security-Policy Hardening (P1-8)

The current CSP likely contains `unsafe-inline` to support Angular's inline styles. This must be removed.

### Problem

Angular uses inline styles for animations and dynamic styling. `unsafe-inline` for `style-src` allows XSS via injected `<style>` tags.

### Fix — CSP Nonce for Angular

Angular 17+ supports `nonce`-based CSP. Add a per-request nonce to both the CSP header and the Angular bootstrap script tag.

#### Backend — generate nonce and set CSP header

```java
// CspFilter.java
@Component
public class CspFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String nonce = Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes());
        req.setAttribute("csp-nonce", nonce);

        String csp = "default-src 'self'; " +
            "script-src 'self' 'nonce-" + nonce + "'; " +
            "style-src 'self' 'nonce-" + nonce + "'; " +
            "img-src 'self' data:; " +       // data: for inline SVG avatars
            "font-src 'self'; " +
            "connect-src 'self'; " +
            "frame-ancestors 'none'; " +
            "form-action 'self'; " +
            "base-uri 'self'";

        res.setHeader("Content-Security-Policy", csp);
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        chain.doFilter(req, res);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        // Only set CSP on HTML responses; skip API responses
        String accept = req.getHeader("Accept");
        return accept != null && !accept.contains("text/html");
    }
}
```

#### Angular — configure nonce in `main.ts`

```typescript
// main.ts
import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';

const nonce = document.querySelector('meta[name="csp-nonce"]')
                ?.getAttribute('content') ?? '';

bootstrapApplication(AppComponent, {
  ...appConfig,
  providers: [
    ...appConfig.providers,
    { provide: CSP_NONCE, useValue: nonce },
  ],
});
```

#### `index.html` — add nonce meta tag (populated by backend template)

```html
<meta name="csp-nonce" content="${cspNonce}">
```

If serving `index.html` as a static file via S3/CloudFront, inject the nonce at the CloudFront edge via Lambda@Edge.

---

## 7. AWS Config Rules (Continuous Compliance)

```hcl
# terraform/config.tf
resource "aws_config_config_rule" "required_rules" {
  for_each = {
    "vpc-flow-logs-enabled"           = {}
    "restricted-ssh"                  = {}
    "restricted-common-ports"         = {}
    "s3-bucket-public-read-prohibited" = {}
    "s3-bucket-server-side-encryption-enabled" = {}
    "rds-storage-encrypted"           = {}
    "rds-instance-public-access-check" = {}
    "guardduty-enabled-centralized"   = {}
    "cloud-trail-enabled"             = {}
    "multi-region-cloudtrail-enabled" = {}
  }

  name = each.key
  source {
    owner             = "AWS"
    source_identifier = upper(replace(each.key, "-", "_"))
  }
}
```

---

## 8. Acceptance Criteria

- [ ] All ECS Fargate tasks run in private subnets (no public IP)
- [ ] RDS and ElastiCache have no public accessibility
- [ ] ALB listener on port 80 redirects to 443 (no plaintext HTTP)
- [ ] WAF attached to ALB — verified via `aws wafv2 list-web-acls`
- [ ] GuardDuty enabled and HIGH+ findings trigger SNS alert
- [ ] VPC Flow Logs enabled and streaming to CloudWatch
- [ ] VPC Endpoints for KMS, S3, Secrets Manager, ECR active
- [ ] `Content-Security-Policy` header does NOT contain `unsafe-inline`
- [ ] CSP nonce present in `<script>` and `<style>` tags
- [ ] `nmap --script ssl-enum-ciphers -p 443 <alb-dns>` — no TLS 1.0/1.1
- [ ] AWS Config rules all GREEN in Console

---

## 9. Related Documents

- [04_GOVCLOUD_MIGRATION.md](04_GOVCLOUD_MIGRATION.md) — VPC is deployed in GovCloud
- [05_AUDIT_SIEM.md](05_AUDIT_SIEM.md) — VPC Flow Logs feed into SIEM
- [06_SECURITY_SCANNING.md](06_SECURITY_SCANNING.md) — WAF complements DAST results
- [08_DATA_PROTECTION.md](08_DATA_PROTECTION.md) — VPC Endpoint for Bedrock enables air-gapped LLM
