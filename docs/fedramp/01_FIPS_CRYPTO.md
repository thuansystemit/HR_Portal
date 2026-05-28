# 01 — FIPS 140-2 Cryptography
> Controls: SC-13 (BLOCKER), SC-12 (BLOCKER), IA-5(1) (Partial)
> Priority: P0-1 (SC-13), P1-1 (SC-12)
> Effort: L — 4–6 weeks

---

## Problem Statement

Three cryptographic gaps block ATO:

| # | File | Issue | Control |
|---|------|-------|---------|
| 1 | `Dockerfile` | `eclipse-temurin:21-jre-alpine` is NOT a FIPS-validated JRE | SC-13 |
| 2 | `JwtConfig.java` | RSA key pair generated in memory on startup — ephemeral, not KMS-backed | SC-12 |
| 3 | `SecurityConfig.java` | `BCryptPasswordEncoder` is NOT FIPS 140-2 validated | IA-5(1) |

FIPS 140-2 requires all cryptographic operations use validated modules. BCrypt and standard Sun JCE are not validated.

---

## 1. FIPS-Validated JRE (SC-13)

### Option A — Azul Zulu FIPS (Recommended for GovCloud)

Azul Zulu Prime JDK/JRE ships with a FIPS-validated cryptographic provider built in. It is the simplest drop-in replacement.

**`Dockerfile` change:**

```dockerfile
# Before
FROM eclipse-temurin:21-jre-alpine

# After
FROM azul/zulu-openjdk:21-jre-latest
# or for FIPS-hardened variant:
# FROM azul/prime:21-jre
```

### Option B — Red Hat UBI with FIPS mode

```dockerfile
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest
```

Red Hat UBI in FIPS mode uses the Red Hat OpenSSL FIPS provider automatically when the host kernel has `fips=1`.

### JVM Startup Flag

Add to `ENTRYPOINT` in `Dockerfile`:

```dockerfile
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dcom.sun.net.ssl.checkRevocation=true", \
  "-Djavax.net.ssl.keyStoreType=PKCS12", \
  "-jar", "app.jar"]
```

---

## 2. Bouncy Castle FIPS Provider (SC-13)

The standard JCE providers (SunJCE, SunRsaSign) are not FIPS-validated. Use Bouncy Castle FIPS (`bc-fips`) as the registered provider.

### `pom.xml` additions

```xml
<!-- Bouncy Castle FIPS -->
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bc-fips</artifactId>
    <version>2.0.0</version>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bctls-fips</artifactId>
    <version>2.0.19</version>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcpkix-fips</artifactId>
    <version>2.0.7</version>
</dependency>
```

Remove `jjwt-impl` and `jjwt-jackson` if switching to Nimbus JOSE (see section 4). If keeping jjwt, ensure it works with BC-FIPS provider.

### Provider Registration

```java
// src/main/java/com/demo/app/config/FipsConfig.java
package com.demo.app.config;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.security.Security;

@Configuration
public class FipsConfig {

    @PostConstruct
    public void registerFipsProvider() {
        if (Security.getProvider("BCFIPS") == null) {
            Security.insertProviderAt(new BouncyCastleFipsProvider(), 1);
        }
        // Optionally remove Sun providers to enforce FIPS-only
        // Security.removeProvider("SunJCE");
        // Security.removeProvider("SunRsaSign");
    }
}
```

---

## 3. Replace BCrypt with PBKDF2-HMAC-SHA256 (IA-5(1))

`BCryptPasswordEncoder` uses the bcrypt algorithm, which is NOT FIPS 140-2 validated. Use `Pbkdf2PasswordEncoder` with SHA-256 — this maps to NIST SP 800-132 PBKDF2.

### `SecurityConfig.java` change

```java
// Before
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}

// After
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import static org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256;

@Bean
public PasswordEncoder passwordEncoder() {
    // 310,000 iterations matches OWASP 2023 recommendation for PBKDF2-SHA256
    return new Pbkdf2PasswordEncoder("", 16, 310_000, PBKDF2WithHmacSHA256);
}
```

### Password Migration Strategy

Existing BCrypt hashes are incompatible with PBKDF2. Use `DelegatingPasswordEncoder` for zero-downtime migration:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    Map<String, PasswordEncoder> encoders = new HashMap<>();
    // New default encoder (FIPS-compliant)
    encoders.put("pbkdf2@sha256", new Pbkdf2PasswordEncoder("", 16, 310_000, PBKDF2WithHmacSHA256));
    // Legacy encoder for reading old hashes (BCrypt hashes in DB prefixed {bcrypt})
    encoders.put("bcrypt", new BCryptPasswordEncoder(12));

    // New passwords written as {pbkdf2@sha256}...
    return new DelegatingPasswordEncoder("pbkdf2@sha256", encoders);
}
```

**Migration steps:**
1. Deploy `DelegatingPasswordEncoder` — reads both; writes PBKDF2.
2. On next successful login, the stored hash is upgraded automatically (Spring Security re-encodes on match if ID differs).
3. After 90 days, require password reset for accounts that haven't logged in (removes remaining BCrypt hashes).
4. After full migration, remove `bcrypt` encoder from the map and drop support.

**Required DB migration:**

The `users.password_hash` column currently stores raw BCrypt strings (`$2a$12$...`). Add the `{bcrypt}` prefix so `DelegatingPasswordEncoder` can identify the algorithm:

```sql
-- Flyway migration: V20__prefix_password_hashes.sql
UPDATE users
SET password_hash = '{bcrypt}' || password_hash
WHERE password_hash NOT LIKE '{%}%';
```

---

## 4. KMS-Backed JWT Signing Key (SC-12)

### Current Problem (`JwtConfig.java`)

```java
// RSA key generated in memory at startup — NEVER do this in production
var gen = KeyPairGenerator.getInstance("RSA");
gen.initialize(2048);
this.keyPair = gen.generateKeyPair();
```

Issues: key is ephemeral (lost on restart), non-audited, non-rotatable, and not stored in a validated HSM.

### Target Architecture — AWS KMS Asymmetric Key

1. **Create a KMS key** (done once, by DevOps):
   ```
   aws kms create-key \
     --key-spec RSA_2048 \
     --key-usage SIGN_VERIFY \
     --description "HR Portal JWT signing key" \
     --region us-gov-west-1
   ```

2. **Signing flow**: `JwtService` calls `kms:Sign` for each token generation. `kms:GetPublicKey` fetches the public key for verification (cached locally).

3. **Verification flow**: Tokens are verified locally using the cached public key — no KMS call needed on every request.

### Updated `JwtConfig.java`

```java
package com.demo.app.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;
import jakarta.annotation.PostConstruct;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

@Configuration
@Getter
public class JwtConfig {

    @Value("${app.jwt.kms-key-id}")          private String kmsKeyId;
    @Value("${app.jwt.access-expiry-seconds}") private long accessExpirySeconds;
    @Value("${app.jwt.refresh-expiry-seconds}") private long refreshExpirySeconds;

    private final KmsClient kmsClient;
    private PublicKey publicKey;

    public JwtConfig(KmsClient kmsClient) {
        this.kmsClient = kmsClient;
    }

    @PostConstruct
    public void init() throws Exception {
        GetPublicKeyResponse resp = kmsClient.getPublicKey(
            GetPublicKeyRequest.builder().keyId(kmsKeyId).build());
        byte[] keyBytes = resp.publicKey().asByteArray();
        this.publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    public String getKmsKeyId() { return kmsKeyId; }
}
```

### Updated `JwtService.java` (signing via KMS)

```java
package com.demo.app.iam.service;

import com.demo.app.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;
    private final KmsClient kmsClient;

    public String generateAccessToken(UUID userId, UUID roleId, Set<String> permissions) {
        var now = System.currentTimeMillis();
        var exp = now + jwtConfig.getAccessExpirySeconds() * 1000L;

        // Build unsigned JWT
        String headerAndPayload = Jwts.builder()
                .subject(userId.toString())
                .claim("roleId", roleId != null ? roleId.toString() : null)
                .claim("permissions", permissions)
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .compact(); // produces header.payload (unsigned)

        // Sign via KMS
        SignResponse signResp = kmsClient.sign(SignRequest.builder()
                .keyId(jwtConfig.getKmsKeyId())
                .message(SdkBytes.fromString(headerAndPayload, StandardCharsets.UTF_8))
                .messageType(MessageType.RAW)
                .signingAlgorithm(SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256)
                .build());

        String sig = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(signResp.signature().asByteArray());
        return headerAndPayload + "." + sig;
    }

    public Claims validateAndParse(String token) {
        return Jwts.parser()
                .verifyWith(jwtConfig.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

> **Note:** If full KMS signing is too complex initially, use KMS to generate and store the key, export it (if exportable) to AWS Secrets Manager, and load it at startup. This still satisfies SC-12 (key not generated in application memory) even if signing is done locally.

### `application.yml` additions

```yaml
app:
  jwt:
    kms-key-id: ${JWT_KMS_KEY_ID}   # ARN or alias of the KMS key
    access-expiry-seconds: 900
    refresh-expiry-seconds: 86400
```

### AWS SDK dependency (`pom.xml`)

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>kms</artifactId>
    <version>2.25.60</version>
</dependency>
```

---

## 5. TLS Cipher Suite Hardening

Restrict to FIPS-approved cipher suites in `application.yml`:

```yaml
server:
  ssl:
    enabled: true
    protocol: TLS
    enabled-protocols: TLSv1.2,TLSv1.3
    ciphers:
      - TLS_AES_256_GCM_SHA384
      - TLS_AES_128_GCM_SHA256
      - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
      - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
      - TLS_DHE_RSA_WITH_AES_256_GCM_SHA384
```

---

## 6. Acceptance Criteria

- [ ] `docker run --rm <image> java -Djava.security.properties=/etc/java/java.security -XshowSettings:all -version 2>&1 | grep -i fips` — shows FIPS provider active
- [ ] New password hash stored as `{pbkdf2@sha256}...` in `users` table
- [ ] Existing `{bcrypt}` hashes still allow login (DelegatingPasswordEncoder)
- [ ] JWT signed with KMS key — verify `x5t` or `kid` header matches KMS key alias
- [ ] `JwtConfig` no longer calls `KeyPairGenerator.getInstance("RSA")`
- [ ] No `BCryptPasswordEncoder` instantiated outside of DelegatingPasswordEncoder legacy map
- [ ] Cipher suite scan: `nmap --script ssl-enum-ciphers -p 443 <host>` — only FIPS-approved suites appear

---

## 7. Related Documents

- [02_MFA.md](02_MFA.md) — MFA uses TOTP (HMAC-SHA1/SHA256), also requires FIPS provider
- [04_GOVCLOUD_MIGRATION.md](04_GOVCLOUD_MIGRATION.md) — KMS key must be in `us-gov-west-1` or `us-gov-east-1`
- [07_SESSION_HARDENING.md](07_SESSION_HARDENING.md) — JWT denylist depends on stable KMS-backed signing keys
