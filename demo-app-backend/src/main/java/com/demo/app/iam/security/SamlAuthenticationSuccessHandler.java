package com.demo.app.iam.security;

import com.demo.app.iam.service.AuthService;
import com.demo.app.iam.service.SamlUserProvisioningService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IA-2(12) / IA-8(2): handles a successful SAML2 assertion by JIT-provisioning
 * or linking the local User account, issuing JWT cookies, and redirecting to the
 * Angular frontend.  Only active when app.saml.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "app.saml.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SamlAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Value("${app.saml.frontend-redirect-url:http://localhost:4200}")
    private String frontendRedirectUrl;

    private final SamlUserProvisioningService provisioningService;
    private final AuthService authService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        var principal = (Saml2AuthenticatedPrincipal) authentication.getPrincipal();

        String provider = principal.getRelyingPartyRegistrationId();
        String nameId   = principal.getName();
        var attributes  = flattenAttributes(principal.getAttributes());

        log.info("IA-2(12): SAML assertion accepted — provider={}", provider);

        var user = provisioningService.provisionOrLink(provider, nameId, null, attributes);
        authService.issueTokensForUser(user.getId(), request, response);

        response.sendRedirect(frontendRedirectUrl);
    }

    /**
     * Collapses the multi-valued SAML attribute map to a flat string map, taking
     * the first value for each attribute (standard single-value IdP attributes).
     */
    static Map<String, String> flattenAttributes(Map<String, List<Object>> samlAttributes) {
        var result = new HashMap<String, String>();
        samlAttributes.forEach((key, values) -> {
            if (values != null) {
                if (!values.isEmpty()) {
                    result.put(key, String.valueOf(values.get(0)));
                }
            }
        });
        return result;
    }
}
