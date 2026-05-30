package com.demo.app.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * IA-2(12) / IA-8(2): builds the SAML2 RelyingPartyRegistration for PIV/CAC
 * and login.gov federation. Only active when app.saml.enabled=true so the
 * SAML2 filter chain does not interfere with existing JWT-based auth in dev.
 */
@Configuration
@ConditionalOnProperty(name = "app.saml.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class Saml2RegistrationConfig {

    private final SamlProperties props;

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        return new InMemoryRelyingPartyRegistrationRepository(buildRegistration());
    }

    RelyingPartyRegistration buildRegistration() {
        return RelyingPartyRegistration
                .withRegistrationId(props.getRegistrationId())
                .entityId(props.getSpEntityId())
                .assertionConsumerServiceLocation(props.getSpAcsUrl())
                .assertionConsumerServiceBinding(Saml2MessageBinding.POST)
                .signingX509Credentials(c -> loadSigningCredentials(c))
                .assertingPartyDetails(idp -> idp
                        .entityId(props.getIdp().getEntityId())
                        .singleSignOnServiceLocation(props.getIdp().getSsoUrl())
                        .singleSignOnServiceBinding(Saml2MessageBinding.REDIRECT)
                        .wantAuthnRequestsSigned(true)
                        .verificationX509Credentials(c -> loadVerificationCredentials(c)))
                .build();
    }

    void loadSigningCredentials(java.util.Collection<Saml2X509Credential> credentials) {
        if (props.getSpSigningKeyPath().isBlank()) {
            log.warn("IA-2(12): app.saml.sp-signing-key-path not set — SP will not sign AuthnRequests (required for login.gov)");
            return;
        }
        try {
            var key  = loadRsaPrivateKey(Path.of(props.getSpSigningKeyPath()));
            var cert = loadX509Certificate(Path.of(props.getSpSigningCertPath()));
            credentials.add(Saml2X509Credential.signing(key, cert));
        } catch (Exception e) {
            throw new IllegalStateException("IA-2(12): failed to load SP signing credentials: " + e.getMessage(), e);
        }
    }

    void loadVerificationCredentials(java.util.Collection<Saml2X509Credential> credentials) {
        if (props.getIdp().getVerificationCertPath().isBlank()) {
            log.warn("IA-2(12): app.saml.idp.verification-cert-path not set — IdP assertions will not be signature-verified (required for production)");
            return;
        }
        try {
            var cert = loadX509Certificate(Path.of(props.getIdp().getVerificationCertPath()));
            credentials.add(Saml2X509Credential.verification(cert));
        } catch (Exception e) {
            throw new IllegalStateException("IA-2(12): failed to load IdP verification certificate: " + e.getMessage(), e);
        }
    }

    static PrivateKey loadRsaPrivateKey(Path path) throws Exception {
        var pem = Files.readString(path)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        var der = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    static X509Certificate loadX509Certificate(Path path) throws Exception {
        var pem = Files.readString(path)
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s+", "");
        var der = Base64.getDecoder().decode(pem);
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
    }
}
