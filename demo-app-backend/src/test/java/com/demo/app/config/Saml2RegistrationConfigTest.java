package com.demo.app.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;

import java.io.FileWriter;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Saml2RegistrationConfigTest {

    // Self-signed RSA-2048 test fixtures (CN=test, valid 10 years from 2026-05-30)
    private static final String TEST_KEY_PEM =
            "-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCuy8gvsmn+xtUm\n" +
            "0gsOEwwvqZqMlhJZoy90E5sZxI7SddLt3Rw2gkNGz2mUujIWhTihemVKgW/9ylAS\n" +
            "xywEugnzar0V+tMgWmm3xTN8m1bEl5mY0TqMa21VLrQUbxSddmzyFER6InRkr8a9\n" +
            "AH+iwph15IyZVyhqKHpovAN9JzbqOxPNGsnP7C4G8ZqJw89ECYee0yA+Z0ARsKnD\n" +
            "Y+jw0JCeoY/fhpz5MEwLVMWc5Z8NZTHiITXJUT31B6cd4B/L0zh/j2Kt9sWLiPlb\n" +
            "wAXgY7POjxTV3Ewfehul6Z9/7dvcgn4tQgWrdHOGIsY/9pketcXVVh8WcJv7IBkb\n" +
            "wU/s1Y8nAgMBAAECggEAERlYNOpEGitDMK/9atwMJiF2P+hyVRh0jOtaEdQ7kHT8\n" +
            "NE6b8u0dPbEOiAEK408W8hzyCa7N0MrYuG2JbdDxJkG7y4mk25ZcnyCIX9kmTMbz\n" +
            "vJJDIvdjU6Dk9tbC8oBiOfAnnrb+IddcHqMM2VQl6+p2oo13I6NgDhzOTpDme1Uw\n" +
            "n6vjIlsdqXyyMZA+A01uD3mSqdsbex7Jxb6X+CShAecjx1T9dZSOkdhOC5c2oQTc\n" +
            "nvIrXvV8QDyaUxZH2RgTzx7YeRxzv4tzwbtb5x6xO4n2guYeGbkNsX1j1fF3ERzo\n" +
            "ZNtVzC2twI6JLyRxGv8DxRYdRBy2q4pUNaB8+TCEWQKBgQDk59VVSEw4lTLC4SIJ\n" +
            "Ftqc1/4mQhZS2ruJNqdQAJEmnsA4hWhulCy7PqxFbKDfOXQd8oHXSeSCAHzFihaA\n" +
            "aY8lUMdpB9XlIkyXpOQYEpeZHBDahegTnhLXNxz82ymgyXdF7jIufe9p3CARCBUh\n" +
            "g0JKDz+POhhpjQApxHO9KETG9QKBgQDDfFnoZqGs0zQiM0GYrERiu/w5OEMjCHd3\n" +
            "qKJVgUEqP3HyBv1yIqW4jhc8HBfHIEVY8fJp6bpP/e6lsVAIv23EY+dWaUaoRIfr\n" +
            "eFykbkwwpndHGItH9x1q56sUeYJDhEpKgR6CV9Px5UHZh1vtRHnNT0HLJlaRiVuk\n" +
            "bnmsaqkUKwKBgHItudnqUCrBMSrIBaQnBDDMBHes61m0xWqyGk7rmXt8IEGROgA+\n" +
            "ZPmZT3DpuGzgSa1Oc7mhoBvAhnw6XvL6tG5WlsErKWQ78ZyKoUPosjmrKDT4KkTu\n" +
            "ylMTqJ/v6hnLpHT7VYifRLa3GD+mmnO292/dn0SgI+Eydexfk6O83GGpAoGADLHm\n" +
            "pNEFbtPIbvAlMmT9i+vpVU3yDjRejcbFxAfA052LKMyLaP4XBQU1PRIV+Th/SGdt\n" +
            "9rLXBprXBKufJvJHjpluTI5JqGPK79/BJGyFRiOJW3Sclu/VWTvyQEIj036j1HLO\n" +
            "KGjLusnInPfNYHsx7cNX4nl65bil2ufRvwCuOkECgYEA3Go7UEms1w2zKoQFrzgl\n" +
            "JylIVqChnqkwS2apW6GG3dIesdmSBuEKSxl5k4NtC23YCWtYDMslOCelMIuvaBFj\n" +
            "aSMXGhmN4kEjywqAM3e9T9la8qltxAtddsIUPKHl0WLK17lLNoGEeKhW2ivpWUbB\n" +
            "2AyoSZOtj8C2YY1FWOanNQI=\n" +
            "-----END PRIVATE KEY-----\n";

    private static final String TEST_CERT_PEM =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICmjCCAYICCQCWA+wNfD94AjANBgkqhkiG9w0BAQsFADAPMQ0wCwYDVQQDDAR0\n" +
            "ZXN0MB4XDTI2MDUzMDEwNDQzMFoXDTM2MDUyNzEwNDQzMFowDzENMAsGA1UEAwwE\n" +
            "dGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAK7LyC+yaf7G1SbS\n" +
            "Cw4TDC+pmoyWElmjL3QTmxnEjtJ10u3dHDaCQ0bPaZS6MhaFOKF6ZUqBb/3KUBLH\n" +
            "LAS6CfNqvRX60yBaabfFM3ybVsSXmZjROoxrbVUutBRvFJ12bPIURHoidGSvxr0A\n" +
            "f6LCmHXkjJlXKGooemi8A30nNuo7E80ayc/sLgbxmonDz0QJh57TID5nQBGwqcNj\n" +
            "6PDQkJ6hj9+GnPkwTAtUxZzlnw1lMeIhNclRPfUHpx3gH8vTOH+PYq32xYuI+VvA\n" +
            "BeBjs86PFNXcTB96G6Xpn3/t29yCfi1CBat0c4Yixj/2mR61xdVWHxZwm/sgGRvB\n" +
            "T+zVjycCAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAZCwb9bOGgTYRI1xai+Xhhp0+\n" +
            "TRuDT6iNrPgOQKAOyLbTGIW1ehgFE4gBrERtOVPeMbrCzN6fIQaqPnEJHEY9+BXb\n" +
            "GLH6JiL7ffHRQGsumceVdRJWFJ3WIsfZYA2t+HvKxxO29hjaFdK6X6QOJkHEuZ5d\n" +
            "Wz/rFfDlkcJM8lWqYuoG+xfGe6+39mdGwK6Am6rKxjA/8kVyIVwreFIBSATmcjRn\n" +
            "o5UU9IWods58emX05Bx5k9kNB5bwnUtEe3IEkaCwWU8oex++76Ce90u2b6ggqb59\n" +
            "meY2hODhIupjEg2mLcnmMWjoqhwC3Qx3sosCrX9K/bVT5ZgT72uJFLs2VE9TpQ==\n" +
            "-----END CERTIFICATE-----\n";

    @TempDir
    Path tempDir;

    Saml2RegistrationConfig config;
    SamlProperties props;

    @BeforeEach
    void setUp() {
        props = new SamlProperties();
        props.setRegistrationId("test-idp");
        props.setSpEntityId("https://sp.example.com/saml2/metadata");
        props.setSpAcsUrl("https://sp.example.com/login/saml2/sso/test-idp");
        props.setSpSigningKeyPath("");
        props.setSpSigningCertPath("");

        SamlProperties.Idp idp = new SamlProperties.Idp();
        idp.setEntityId("https://idp.example.com");
        idp.setSsoUrl("https://idp.example.com/sso");
        idp.setVerificationCertPath("");
        props.setIdp(idp);

        config = new Saml2RegistrationConfig(props);
    }

    @Test
    void buildRegistration_setsRegistrationIdAndSpEntityId() {
        RelyingPartyRegistration reg = config.buildRegistration();

        assertThat(reg.getRegistrationId()).isEqualTo("test-idp");
        assertThat(reg.getEntityId()).isEqualTo("https://sp.example.com/saml2/metadata");
    }

    @Test
    void buildRegistration_setsAcsUrl() {
        RelyingPartyRegistration reg = config.buildRegistration();

        assertThat(reg.getAssertionConsumerServiceLocation())
                .isEqualTo("https://sp.example.com/login/saml2/sso/test-idp");
    }

    @Test
    void buildRegistration_setsIdpEntityIdAndSsoUrl() {
        RelyingPartyRegistration reg = config.buildRegistration();

        assertThat(reg.getAssertingPartyDetails().getEntityId())
                .isEqualTo("https://idp.example.com");
        assertThat(reg.getAssertingPartyDetails().getSingleSignOnServiceLocation())
                .isEqualTo("https://idp.example.com/sso");
    }

    @Test
    void loadSigningCredentials_whenPathBlank_addsNoCredentials() {
        var credentials = new ArrayList<Saml2X509Credential>();
        config.loadSigningCredentials(credentials);

        assertThat(credentials).isEmpty();
    }

    @Test
    void loadVerificationCredentials_whenPathBlank_addsNoCredentials() {
        var credentials = new ArrayList<Saml2X509Credential>();
        config.loadVerificationCredentials(credentials);

        assertThat(credentials).isEmpty();
    }

    @Test
    void loadSigningCredentials_withValidKeyAndCert_addsSigningCredential() throws Exception {
        Path keyPath  = writeFile("sp.key", TEST_KEY_PEM);
        Path certPath = writeFile("sp.crt", TEST_CERT_PEM);

        props.setSpSigningKeyPath(keyPath.toString());
        props.setSpSigningCertPath(certPath.toString());

        var credentials = new ArrayList<Saml2X509Credential>();
        config.loadSigningCredentials(credentials);

        assertThat(credentials).hasSize(1);
        assertThat(credentials.get(0).getCredentialTypes())
                .contains(Saml2X509Credential.Saml2X509CredentialType.SIGNING);
    }

    @Test
    void loadVerificationCredentials_withValidCert_addsVerificationCredential() throws Exception {
        Path certPath = writeFile("idp.crt", TEST_CERT_PEM);
        props.getIdp().setVerificationCertPath(certPath.toString());

        var credentials = new ArrayList<Saml2X509Credential>();
        config.loadVerificationCredentials(credentials);

        assertThat(credentials).hasSize(1);
        assertThat(credentials.get(0).getCredentialTypes())
                .contains(Saml2X509Credential.Saml2X509CredentialType.VERIFICATION);
    }

    @Test
    void loadSigningCredentials_withInvalidKeyContent_throwsIllegalState() throws Exception {
        Path keyPath  = writeFile("bad.key", "-----BEGIN PRIVATE KEY-----\nbaddata\n-----END PRIVATE KEY-----\n");
        Path certPath = writeFile("sp.crt", TEST_CERT_PEM);

        props.setSpSigningKeyPath(keyPath.toString());
        props.setSpSigningCertPath(certPath.toString());

        assertThatThrownBy(() -> config.loadSigningCredentials(new ArrayList<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IA-2(12)");
    }

    @Test
    void loadVerificationCredentials_withInvalidCertContent_throwsIllegalState() throws Exception {
        Path certPath = writeFile("bad.crt", "-----BEGIN CERTIFICATE-----\nbaddata\n-----END CERTIFICATE-----\n");
        props.getIdp().setVerificationCertPath(certPath.toString());

        assertThatThrownBy(() -> config.loadVerificationCredentials(new ArrayList<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IA-2(12)");
    }

    @Test
    void loadRsaPrivateKey_parsesKey() throws Exception {
        Path keyPath = writeFile("sp.key", TEST_KEY_PEM);

        var key = Saml2RegistrationConfig.loadRsaPrivateKey(keyPath);

        assertThat(key.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void loadX509Certificate_parsesCert() throws Exception {
        Path certPath = writeFile("sp.crt", TEST_CERT_PEM);

        X509Certificate cert = Saml2RegistrationConfig.loadX509Certificate(certPath);

        assertThat(cert.getType()).isEqualTo("X.509");
        assertThat(cert.getSubjectX500Principal().getName()).contains("CN=test");
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private Path writeFile(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        try (var fw = new FileWriter(path.toFile())) {
            fw.write(content);
        }
        return path;
    }
}
