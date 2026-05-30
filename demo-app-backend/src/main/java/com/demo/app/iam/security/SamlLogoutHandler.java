package com.demo.app.iam.security;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.iam.repository.FederatedIdentityRepository;
import com.demo.app.iam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * IA-2(12) / IA-8(2): handles SAML2 Single Log-Out (SLO) by revoking the local
 * JWT session and emitting a {@code SAML_USER_LOGGED_OUT} audit event when IdP
 * context is available.  Only active when app.saml.enabled=true.
 *
 * <p>JWT revocation is delegated to {@link AuthService#logout} so the same
 * denylist + session deregister + cookie-clear logic runs for both SLO and
 * standard logout.  The SAML-specific audit adds IdP provider / nameId context
 * on top of the {@code USER_LOGGED_OUT} event already emitted by that path.
 */
@Component
@ConditionalOnProperty(name = "app.saml.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SamlLogoutHandler implements LogoutHandler {

    static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final AuthService authService;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response,
                       Authentication authentication) {
        // AC-12 / AC-10: revoke JWT, clear cookies — same path as regular logout
        authService.logout(request, response);

        // Emit SAML-specific audit event when we have IdP context.
        // In the STATELESS JWT model the SAML session may already be gone by SLO
        // time, so treat a missing/non-SAML principal as a no-op for this path.
        if (authentication != null) {
            if (authentication.getPrincipal() instanceof Saml2AuthenticatedPrincipal principal) {
                String provider = principal.getRelyingPartyRegistrationId();
                String nameId   = principal.getName();
                log.info("IA-2(12): SAML SLO received — provider={}", provider);
                federatedIdentityRepository.findByProviderAndNameId(provider, nameId)
                        .ifPresent(fi -> auditService.log(
                                SYSTEM_ACTOR, "SAML_USER_LOGGED_OUT", "User", fi.getUserId(),
                                null, Map.of("provider", provider, "nameId", nameId), "success"));
            }
        }
    }
}
