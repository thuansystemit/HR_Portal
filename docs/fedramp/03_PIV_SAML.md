# 03 — PIV/CAC Smart Card + SAML Federation
> Controls: IA-2(12) (BLOCKER), IA-8(2) (Gap)
> Priority: P0-3
> Effort: XL — 6–10 weeks

---

## Problem Statement

FedRAMP Moderate mandates PIV/CAC (Personal Identity Verification / Common Access Card) authentication for federal employees (IA-2(12)). Currently no SAML or OIDC federation exists — users authenticate via username/password only.

This is a hard ATO blocker. The system must federate with an approved federal identity provider (login.gov) and accept PIV-derived assertions.

---

## 1. Architecture Overview

```
Browser
  │
  ├── GET /api/v1/auth/saml/login  → SP-initiated SAML flow
  │
  └── Redirect to login.gov IdP
            │
            ├── User authenticates with PIV card
            ├── login.gov issues SAML Assertion
            │
  ┌─────────▼──────────────┐
  │  /api/v1/auth/saml/acs │  ← Assertion Consumer Service
  │  (POST, public)        │
  └─────────┬──────────────┘
            │
            ├── Validate assertion signature against login.gov cert
            ├── Extract edipi / UID attribute
            ├── JIT-provision user if new (federated_identity table)
            └── Issue HR Portal JWT cookies (existing flow)
```

---

## 2. login.gov Integration

login.gov is the U.S. federal shared identity provider. It supports:
- PIV/CAC authentication (IAL2 / AAL3)
- SAML 2.0 SP-initiated SSO
- PKCE OIDC (alternative)

**Key login.gov requirements:**
- Your SP must be registered at partners.login.gov
- Metadata exchange required (SP metadata → login.gov; IdP metadata → your app)
- Private key for AuthnRequest signing must be stored in KMS (see [01_FIPS_CRYPTO.md](01_FIPS_CRYPTO.md))
- SP `entityId` must be a stable HTTPS URI (your production URL)
- `NameIDFormat` should be `urn:oasis:names:tc:SAML:2.0:nameid-format:persistent`

---

## 3. Database Schema

```sql
-- Flyway migration: V23__federated_identity.sql
CREATE TABLE federated_identities (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider            VARCHAR(50) NOT NULL,      -- e.g. 'login.gov'
    external_id         TEXT        NOT NULL,      -- NameID from IdP (persistent)
    email               TEXT,
    display_name        TEXT,
    attributes          JSONB,                     -- raw assertion attributes
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at       TIMESTAMPTZ,
    UNIQUE (provider, external_id)
);
CREATE INDEX idx_fed_identity_user ON federated_identities(user_id);
```

---

## 4. Backend Implementation

### Dependencies (`pom.xml`)

```xml
<!-- Spring Security SAML2 -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-saml2-service-provider</artifactId>
</dependency>

<!-- OpenSAML (transitive via spring-security-saml2, but pin version) -->
<dependency>
    <groupId>org.opensaml</groupId>
    <artifactId>opensaml-saml-impl</artifactId>
    <version>4.3.2</version>
</dependency>
```

### `SamlConfig.java`

```java
package com.demo.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.saml2.provider.service.registration.*;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.beans.factory.annotation.Value;

import java.security.cert.X509Certificate;
import java.security.PrivateKey;

@Configuration
public class SamlConfig {

    @Value("${saml.idp.metadata-uri}")  private String idpMetadataUri;
    @Value("${saml.sp.entity-id}")      private String spEntityId;

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository(
            PrivateKey spSigningKey,           // injected from KMS/Secrets Manager
            X509Certificate spSigningCert) {

        RelyingPartyRegistration loginGov = RelyingPartyRegistrations
            .fromMetadataLocation(idpMetadataUri)
            .registrationId("login-gov")
            .entityId(spEntityId)
            .assertionConsumerServiceLocation("{baseUrl}/api/v1/auth/saml/acs")
            .signingX509Credentials(c -> c.add(
                Saml2X509Credential.signing(spSigningKey, spSigningCert)))
            .build();

        return new InMemoryRelyingPartyRegistrationRepository(loginGov);
    }
}
```

### SAML routes in `SecurityConfig.java`

```java
// Add to filterChain:
.saml2Login(saml -> saml
    .loginProcessingUrl("/api/v1/auth/saml/acs")
    .successHandler(samlAuthenticationSuccessHandler())
)
.saml2Metadata(Customizer.withDefaults())  // exposes /saml2/service-provider-metadata/login-gov
```

### `SamlAuthenticationSuccessHandler.java`

```java
package com.demo.app.iam.security;

import com.demo.app.iam.service.FederatedIdentityService;
import com.demo.app.iam.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SamlAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final FederatedIdentityService federatedIdentityService;
    private final JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req,
                                        HttpServletResponse res,
                                        Authentication auth) {
        var principal = (Saml2AuthenticatedPrincipal) auth.getPrincipal();
        String externalId = principal.getName();
        String email = principal.getFirstAttribute("email");

        // JIT-provision or load existing user
        var user = federatedIdentityService.resolveUser("login.gov", externalId, email, principal);

        // Issue JWT cookies (reuse existing JwtService)
        String accessToken  = jwtService.generateAccessToken(user.getId(), user.getRoleId(), user.getPermissions());
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        setJwtCookies(res, accessToken, refreshToken);

        res.sendRedirect("/dashboard");
    }
}
```

### `FederatedIdentityService.java`

```java
package com.demo.app.iam.service;

import com.demo.app.iam.entity.FederatedIdentity;
import com.demo.app.iam.entity.User;
import com.demo.app.iam.repository.FederatedIdentityRepository;
import com.demo.app.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FederatedIdentityService {

    private final FederatedIdentityRepository federatedIdentityRepository;
    private final UserRepository userRepository;

    @Transactional
    public User resolveUser(String provider, String externalId, String email,
                             Saml2AuthenticatedPrincipal principal) {
        return federatedIdentityRepository
            .findByProviderAndExternalId(provider, externalId)
            .map(fi -> {
                fi.setLastLoginAt(Instant.now());
                return fi.getUser();
            })
            .orElseGet(() -> {
                // JIT provision new user
                User newUser = User.builder()
                    .email(email)
                    .name(principal.getFirstAttribute("given_name") + " " + principal.getFirstAttribute("family_name"))
                    .passwordHash("")  // no local password for federated users
                    .mfaEnabled(true)  // PIV counts as MFA
                    .build();
                userRepository.save(newUser);

                FederatedIdentity fi = FederatedIdentity.builder()
                    .user(newUser)
                    .provider(provider)
                    .externalId(externalId)
                    .email(email)
                    .lastLoginAt(Instant.now())
                    .build();
                federatedIdentityRepository.save(fi);

                return newUser;
            });
    }
}
```

---

## 5. SP Metadata Registration with login.gov

Generate SP metadata and register at `partners.login.gov`:

```bash
# After app is running, fetch SP metadata:
curl https://<your-domain>/saml2/service-provider-metadata/login-gov > sp-metadata.xml
```

Submit `sp-metadata.xml` to login.gov partner portal with:
- `entityId`: your production HTTPS URI
- `acs`: `https://<your-domain>/api/v1/auth/saml/acs`
- SP signing certificate (public key only)
- Requested AAL: AAL2 minimum, AAL3 for PIV
- Requested IAL: IAL2

---

## 6. `application.yml` additions

```yaml
saml:
  sp:
    entity-id: https://hrportal.agency.gov/saml2/service-provider-metadata/login-gov
  idp:
    metadata-uri: https://idp.int.identitysandbox.gov/api/saml/metadata2024   # sandbox
    # Production: https://idp.login.gov/api/saml/metadata2024
```

---

## 7. Angular Frontend

### New login option on login page

```html
<!-- Add below the password form -->
<div class="text-center my-3">
  <span class="text-muted small">— or —</span>
</div>
<a href="/saml2/authenticate/login-gov"
   class="btn btn-outline-primary w-100 d-flex align-items-center justify-content-center gap-2">
  <img src="assets/login-gov-logo.svg" height="20" alt="login.gov">
  Sign in with PIV / login.gov
</a>
```

### Route guard for PIV-only resources

Some resources (e.g., export PII data) may require PIV authentication. Add a `pivRequiredGuard`:

```typescript
export const pivRequiredGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (!auth.user()?.federatedProvider) {
    return inject(Router).createUrlTree(['/auth/piv-required']);
  }
  return true;
};
```

---

## 8. Acceptance Criteria

- [ ] `GET /saml2/authenticate/login-gov` redirects to login.gov IdP
- [ ] After PIV authentication at login.gov, `POST /api/v1/auth/saml/acs` receives assertion and issues JWT cookies
- [ ] New user is provisioned in `users` + `federated_identities` tables on first login
- [ ] Existing user is found by `(provider, externalId)` on subsequent logins
- [ ] `federated_identities.last_login_at` updated on each login
- [ ] Federated user's `mfa_enabled = true` (PIV counts as MFA)
- [ ] SP metadata endpoint `/saml2/service-provider-metadata/login-gov` returns valid XML
- [ ] Audit event logged for SAML login success and failure
- [ ] login.gov sandbox login works end-to-end in staging environment

---

## 9. Related Documents

- [01_FIPS_CRYPTO.md](01_FIPS_CRYPTO.md) — SP signing key must be KMS-backed
- [02_MFA.md](02_MFA.md) — PIV satisfies IA-2(12); TOTP satisfies IA-2(1)/IA-2(2) for non-PIV users
- [04_GOVCLOUD_MIGRATION.md](04_GOVCLOUD_MIGRATION.md) — login.gov integration only works from GovCloud endpoint
- [05_AUDIT_SIEM.md](05_AUDIT_SIEM.md) — SAML assertion events must be audited
