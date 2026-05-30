package com.demo.app.iam.security;

import com.demo.app.iam.entity.User;
import com.demo.app.iam.service.AuthService;
import com.demo.app.iam.service.SamlUserProvisioningService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SamlAuthenticationSuccessHandlerTest {

    @Mock SamlUserProvisioningService provisioningService;
    @Mock AuthService authService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock Authentication authentication;
    @Mock Saml2AuthenticatedPrincipal principal;

    SamlAuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SamlAuthenticationSuccessHandler(provisioningService, authService);
        ReflectionTestUtils.setField(handler, "frontendRedirectUrl", "http://localhost:4200");
    }

    @Test
    void onAuthenticationSuccess_provisionsUserIssuesTokensAndRedirects() throws Exception {
        var userId = UUID.randomUUID();
        var user = User.builder().id(userId).email("jane@agency.gov").fullName("Jane Doe").build();

        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getRelyingPartyRegistrationId()).thenReturn("logingov");
        when(principal.getName()).thenReturn("urn:test:sub:12345");
        when(principal.getAttributes()).thenReturn(Map.of("email", List.of("jane@agency.gov")));
        when(provisioningService.provisionOrLink(any(), any(), isNull(), any())).thenReturn(user);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(provisioningService).provisionOrLink(eq("logingov"), eq("urn:test:sub:12345"), isNull(), any());
        verify(authService).issueTokensForUser(eq(userId), eq(request), eq(response));
        verify(response).sendRedirect("http://localhost:4200");
    }

    @Test
    void onAuthenticationSuccess_passesAttributesToProvisioningService() throws Exception {
        var user = User.builder().id(UUID.randomUUID()).email("jane@agency.gov").fullName("Jane Doe").build();
        var samlAttrs = Map.of(
                "email",       List.<Object>of("jane@agency.gov"),
                "given_name",  List.<Object>of("Jane"),
                "family_name", List.<Object>of("Doe"));

        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getRelyingPartyRegistrationId()).thenReturn("logingov");
        when(principal.getName()).thenReturn("sub:123");
        when(principal.getAttributes()).thenReturn(samlAttrs);
        when(provisioningService.provisionOrLink(any(), any(), any(), any())).thenReturn(user);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(provisioningService).provisionOrLink(
                eq("logingov"), eq("sub:123"), isNull(),
                argThat(attrs ->
                        "jane@agency.gov".equals(attrs.get("email")) &&
                        "Jane".equals(attrs.get("given_name")) &&
                        "Doe".equals(attrs.get("family_name"))));
    }

    // ── flattenAttributes ────────────────────────────────────────────────────

    @Test
    void flattenAttributes_takesFirstValuePerKey() {
        var attrs = Map.of(
                "email", List.<Object>of("jane@agency.gov", "other@agency.gov"),
                "name",  List.<Object>of("Jane Doe"));

        var result = SamlAuthenticationSuccessHandler.flattenAttributes(attrs);

        assertThat(result.get("email")).isEqualTo("jane@agency.gov");
        assertThat(result.get("name")).isEqualTo("Jane Doe");
    }

    @Test
    void flattenAttributes_emptyValueList_skipsKey() {
        var attrs = new HashMap<String, List<Object>>();
        attrs.put("email", List.of());
        attrs.put("name",  List.of("Jane Doe"));

        var result = SamlAuthenticationSuccessHandler.flattenAttributes(attrs);

        assertThat(result).doesNotContainKey("email");
        assertThat(result.get("name")).isEqualTo("Jane Doe");
    }

    @Test
    void flattenAttributes_nullValueList_skipsKey() {
        var attrs = new HashMap<String, List<Object>>();
        attrs.put("email", null);
        attrs.put("name",  List.of("Jane Doe"));

        var result = SamlAuthenticationSuccessHandler.flattenAttributes(attrs);

        assertThat(result).doesNotContainKey("email");
        assertThat(result.get("name")).isEqualTo("Jane Doe");
    }

    @Test
    void flattenAttributes_emptyInput_returnsEmptyMap() {
        assertThat(SamlAuthenticationSuccessHandler.flattenAttributes(Map.of())).isEmpty();
    }
}
