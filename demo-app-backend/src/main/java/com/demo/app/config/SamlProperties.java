package com.demo.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * IA-2(12) / IA-8(2): configuration for the SAML2 Service Provider.
 * Inactive until app.saml.enabled=true and all required fields are populated.
 */
@Component
@ConfigurationProperties(prefix = "app.saml")
@Data
public class SamlProperties {

    /** Master switch — keeps SAML2 beans off the classpath path during dev. */
    private boolean enabled = false;

    /** Angular frontend URL to redirect to after a successful SSO assertion. */
    private String frontendRedirectUrl = "http://localhost:4200";

    /** Spring Security registration ID, used in URLs like /login/saml2/sso/{id}. */
    private String registrationId = "logingov";

    // ── Service Provider (this application) ──────────────────────────────────

    /** SP entity ID, e.g. https://hrportal.agency.gov/saml2/metadata */
    private String spEntityId = "";

    /**
     * Assertion Consumer Service URL where the IdP posts the SAML response.
     * e.g. https://hrportal.agency.gov/login/saml2/sso/logingov
     */
    private String spAcsUrl = "";

    /** Absolute path to the SP RSA private key PEM file (PKCS8). Required in prod. */
    private String spSigningKeyPath = "";

    /** Absolute path to the SP X.509 certificate PEM file matching the signing key. */
    private String spSigningCertPath = "";

    // ── Identity Provider ─────────────────────────────────────────────────────

    private Idp idp = new Idp();

    @Data
    public static class Idp {

        /** IdP entity ID, e.g. https://idp.int.identitysandbox.gov */
        private String entityId = "";

        /** IdP SSO redirect URL for AuthnRequest submissions. */
        private String ssoUrl = "";

        /**
         * URL of the IdP SAML2 metadata document.
         * When present the SP can fetch and cache IdP certificates automatically.
         * e.g. https://idp.int.identitysandbox.gov/api/saml/metadata2024
         */
        private String metadataUrl = "";

        /** Absolute path to the IdP X.509 verification certificate PEM file. */
        private String verificationCertPath = "";
    }
}
