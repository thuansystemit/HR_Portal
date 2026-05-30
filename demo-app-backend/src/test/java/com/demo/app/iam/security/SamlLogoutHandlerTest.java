package com.demo.app.iam.security;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.iam.entity.FederatedIdentity;
import com.demo.app.iam.repository.FederatedIdentityRepository;
import com.demo.app.iam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SamlLogoutHandlerTest {

    @Mock AuthService authService;
    @Mock FederatedIdentityRepository federatedIdentityRepository;
    @Mock AuditService auditService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;

    SamlLogoutHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SamlLogoutHandler(authService, federatedIdentityRepository, auditService);
    }

    @Test
    void logout_nullAuthentication_revokesJwtOnly() {
        handler.logout(request, response, null);

        verify(authService).logout(request, response);
        verifyNoInteractions(federatedIdentityRepository, auditService);
    }

    @Test
    void logout_nonSamlPrincipal_revokesJwtOnly() {
        var auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("plain-string-principal");

        handler.logout(request, response, auth);

        verify(authService).logout(request, response);
        verifyNoInteractions(federatedIdentityRepository, auditService);
    }

    @Test
    void logout_samlPrincipal_federatedIdentityFound_emitsSamlAudit() {
        var userId = UUID.randomUUID();
        var fi = FederatedIdentity.builder()
                .userId(userId).provider("logingov").nameId("sub:123").build();

        var principal = mock(Saml2AuthenticatedPrincipal.class);
        when(principal.getRelyingPartyRegistrationId()).thenReturn("logingov");
        when(principal.getName()).thenReturn("sub:123");

        var auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);

        when(federatedIdentityRepository.findByProviderAndNameId("logingov", "sub:123"))
                .thenReturn(Optional.of(fi));

        handler.logout(request, response, auth);

        verify(authService).logout(request, response);
        verify(auditService).log(
                eq(SamlLogoutHandler.SYSTEM_ACTOR),
                eq("SAML_USER_LOGGED_OUT"),
                eq("User"),
                eq(userId),
                isNull(),
                argThat(m -> "logingov".equals(m.get("provider")) && "sub:123".equals(m.get("nameId"))),
                eq("success"));
    }

    @Test
    void logout_samlPrincipal_federatedIdentityNotFound_noAudit() {
        var principal = mock(Saml2AuthenticatedPrincipal.class);
        when(principal.getRelyingPartyRegistrationId()).thenReturn("logingov");
        when(principal.getName()).thenReturn("sub:unknown");

        var auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);

        when(federatedIdentityRepository.findByProviderAndNameId("logingov", "sub:unknown"))
                .thenReturn(Optional.empty());

        handler.logout(request, response, auth);

        verify(authService).logout(request, response);
        verifyNoInteractions(auditService);
    }

    @Test
    void logout_samlPrincipal_alwaysRevokesJwtRegardlessOfIdentityLookup() {
        var principal = mock(Saml2AuthenticatedPrincipal.class);
        when(principal.getRelyingPartyRegistrationId()).thenReturn("logingov");
        when(principal.getName()).thenReturn("sub:123");

        var auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);

        when(federatedIdentityRepository.findByProviderAndNameId(any(), any()))
                .thenReturn(Optional.empty());

        handler.logout(request, response, auth);

        verify(authService).logout(request, response);
    }
}
