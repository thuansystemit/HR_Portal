# 07 — Server-Side Session Hardening
> Controls: AC-10 (Gap), AC-11 (Partial), AC-12 (Partial), IA-3 (Gap — internal API key)
> Priority: P1-5, P1-9
> Effort: S — 1–2 weeks

---

## Problem Statement

| Gap | File | Issue | Control |
|-----|------|-------|---------|
| No concurrent session limit | `JwtService.java` | A user can have unlimited simultaneous sessions | AC-10 |
| Client-side idle timeout only | Frontend only | Server can't enforce session lock; bypassable via API | AC-11 |
| Access token not revocable | `JwtService.java` | 15-min tokens can't be invalidated before expiry | AC-12 |
| Static hardcoded API key | `InternalApiKeyFilter.java` | Default empty key; rotated manually | IA-3 |

---

## 1. JWT Denylist for Token Revocation (AC-12)

FedRAMP requires the ability to terminate sessions immediately (e.g., on logout, compromise, or account deactivation). Stateless JWTs can't be revoked without a server-side record.

### Strategy: Redis-backed denylist

The existing `spring-boot-starter-data-redis` dependency covers this.

### Add `jti` (JWT ID) to tokens

```java
// JwtService.java — add jti claim
public String generateAccessToken(UUID userId, UUID roleId, Set<String> permissions) {
    var now = System.currentTimeMillis();
    var exp = now + jwtConfig.getAccessExpirySeconds() * 1000L;
    String jti = UUID.randomUUID().toString();   // unique token ID

    return Jwts.builder()
            .id(jti)                             // sets "jti" claim
            .subject(userId.toString())
            .claim("roleId", roleId != null ? roleId.toString() : null)
            .claim("permissions", permissions)
            .issuedAt(new Date(now))
            .expiration(new Date(exp))
            .signWith(jwtConfig.getPrivateKey(), Jwts.SIG.RS256)
            .compact();
}
```

### Redis denylist service

```java
package com.demo.app.iam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenDenylistService {

    private static final String PREFIX = "token:denied:";
    private final StringRedisTemplate redisTemplate;

    public void deny(String jti, Instant expiry) {
        Duration ttl = Duration.between(Instant.now(), expiry);
        if (!ttl.isNegative()) {
            redisTemplate.opsForValue().set(PREFIX + jti, "1", ttl);
        }
    }

    public boolean isDenied(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
    }
}
```

### Update `JwtCookieAuthFilter.java`

```java
// After parsing token, check denylist
Claims claims = jwtService.validateAndParse(token);
String jti = claims.getId();
if (jti != null && tokenDenylistService.isDenied(jti)) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    return;
}
```

### Update `AuthService.logout()`

```java
public void logout(String accessToken, String refreshToken) {
    // Deny both tokens immediately
    Claims accessClaims = jwtService.validateAndParse(accessToken);
    tokenDenylistService.deny(accessClaims.getId(), accessClaims.getExpiration().toInstant());

    if (refreshToken != null) {
        Claims refreshClaims = jwtService.validateAndParse(refreshToken);
        tokenDenylistService.deny(refreshClaims.getId(), refreshClaims.getExpiration().toInstant());
    }
    // Clear cookies
}
```

---

## 2. Server-Side Idle Timeout (AC-11)

NIST AC-11 requires the session to lock after a period of inactivity. Currently only the Angular frontend handles this — the backend accepts any valid token until expiry.

### Approach: Activity tracking in Redis

```java
package com.demo.app.iam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SessionActivityService {

    private static final String PREFIX = "session:activity:";

    @Value("${app.session.idle-timeout-seconds:1800}")  // 30 min default (FedRAMP: ≤ 30 min)
    private long idleTimeoutSeconds;

    private final StringRedisTemplate redisTemplate;

    public void touch(String jti) {
        redisTemplate.opsForValue().set(PREFIX + jti, "1", Duration.ofSeconds(idleTimeoutSeconds));
    }

    public boolean isIdle(String jti) {
        return !Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
    }
}
```

### Update `JwtCookieAuthFilter.java`

```java
// After denylist check:
if (sessionActivityService.isIdle(jti)) {
    // Session timed out — revoke and return 401
    tokenDenylistService.deny(jti, claims.getExpiration().toInstant());
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    return;
}
// Touch to extend idle timer
sessionActivityService.touch(jti);
```

### `application.yml` config

```yaml
app:
  session:
    idle-timeout-seconds: 1800    # 30 minutes (FedRAMP AC-11 maximum)
    max-concurrent-sessions: 3    # AC-10
```

---

## 3. Concurrent Session Limit (AC-10)

Limit the number of simultaneous active sessions per user.

```java
package com.demo.app.iam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConcurrentSessionService {

    private static final String PREFIX = "sessions:user:";

    @Value("${app.session.max-concurrent-sessions:3}")
    private int maxConcurrentSessions;

    private final StringRedisTemplate redisTemplate;

    public void registerSession(UUID userId, String jti, long expirySeconds) {
        String key = PREFIX + userId;
        redisTemplate.opsForSet().add(key, jti);
        redisTemplate.expire(key, Duration.ofSeconds(expirySeconds));

        // If over limit, revoke oldest sessions (LIFO — evict first added)
        Long count = redisTemplate.opsForSet().size(key);
        if (count != null && count > maxConcurrentSessions) {
            // Evict excess — pop from set (oldest approximation)
            for (int i = 0; i < count - maxConcurrentSessions; i++) {
                String evicted = redisTemplate.opsForSet().pop(key);
                if (evicted != null) {
                    tokenDenylistService.deny(evicted, Instant.now().plusSeconds(expirySeconds));
                }
            }
        }
    }

    public void removeSession(UUID userId, String jti) {
        redisTemplate.opsForSet().remove(PREFIX + userId, jti);
    }
}
```

Call `registerSession()` in `AuthService` after issuing tokens.

---

## 4. Replace Static Internal API Key with mTLS (IA-3)

### Current problem

`InternalApiKeyFilter.java` reads from `app.internal.api-key` which defaults to empty string. The key is static and not rotated.

### Short-term fix — Secrets Manager rotation

Until mTLS is implemented:

1. Store the key in AWS Secrets Manager
2. Enable automatic rotation (30-day cycle via Lambda rotator)
3. Remove the `default=""` fallback — fail open if key is blank

```java
// InternalApiKeyFilter.java — remove the blank default
@Value("${app.internal.api-key}")  // No default — must be set explicitly
private String internalApiKey;

@Override
protected void doFilterInternal(...) {
    if (internalApiKey == null || internalApiKey.isBlank()) {
        // Misconfigured — deny all internal requests
        chain.doFilter(request, response);
        return;
    }
    // ... existing logic
}
```

### Long-term fix — mTLS for service-to-service

Replace the `X-Internal-Api-Key` header with mutual TLS client certificates:

1. Issue client cert to each internal service (signed by internal CA)
2. Configure the backend to require client cert for `/api/v1/knowledge/ingest`
3. Remove `InternalApiKeyFilter` entirely

```yaml
# application.yml — mTLS for internal endpoints
server:
  ssl:
    client-auth: need           # Require client cert
    trust-store: classpath:internal-ca-truststore.p12
    trust-store-password: ${SSL_TRUST_STORE_PASSWORD}
    trust-store-type: PKCS12
```

---

## 5. Angular — Server-Driven Idle Timeout

Update the Angular idle handler to respond to server-side 401 (session expired):

```typescript
// auth.interceptor.ts — handle 401 from idle timeout
intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
  return next.handle(req).pipe(
    catchError(error => {
      if (error.status === 401) {
        this.authService.clearSession();
        this.router.navigate(['/auth/login'], {
          queryParams: { reason: 'session_expired' }
        });
      }
      return throwError(() => error);
    })
  );
}
```

Display a meaningful message on the login page when `reason=session_expired`:

```html
@if (sessionExpired()) {
  <div class="alert alert-warning small" role="alert">
    Your session has expired due to inactivity. Please log in again.
  </div>
}
```

---

## 6. Flyway Migration

```sql
-- No schema changes needed for Redis-backed session management
-- Add jti column to audit_events (see 05_AUDIT_SIEM.md)
```

---

## 7. Acceptance Criteria

- [ ] `POST /api/v1/auth/logout` denies both access and refresh tokens in Redis
- [ ] After logout, using the old access token returns HTTP 401
- [ ] After 30 minutes of inactivity, next API call returns HTTP 401
- [ ] User with 4 active sessions: oldest is revoked automatically
- [ ] `app.internal.api-key` missing/blank → internal requests rejected (not accepted)
- [ ] JWT `jti` claim present on all issued tokens
- [ ] `session_id` (jti) populated in audit events
- [ ] Angular login page shows "session expired" message when redirected after idle timeout

---

## 8. Related Documents

- [01_FIPS_CRYPTO.md](01_FIPS_CRYPTO.md) — KMS-backed JWT signing is prerequisite for stable `jti` tracking
- [02_MFA.md](02_MFA.md) — MFA challenge tokens follow the same Redis TTL pattern
- [05_AUDIT_SIEM.md](05_AUDIT_SIEM.md) — `jti` as `session_id` in audit events (AU-3)
