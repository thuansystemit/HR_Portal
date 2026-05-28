package com.demo.app.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;

@Configuration
@Getter
@Slf4j
public class JwtConfig {

    @Value("${app.jwt.access-expiry-seconds}")
    private long accessExpirySeconds;

    @Value("${app.jwt.refresh-expiry-seconds}")
    private long refreshExpirySeconds;

    // Keystore path — mount as a Docker/K8s secret in non-dev environments.
    // In GovCloud this will be replaced by KMS (SC-12 full compliance).
    @Value("${app.jwt.keystore.path:}")
    private String keystorePath;

    @Value("${app.jwt.keystore.password:changeit}")
    private String keystorePassword;

    @Value("${app.jwt.keystore.alias:hrportal-jwt}")
    private String keystoreAlias;

    private KeyPair keyPair;

    @PostConstruct
    public void init() throws Exception {
        if (keystorePath != null && !keystorePath.isBlank()) {
            keyPair = loadFromKeystore();
            log.info("JWT signing key loaded from keystore: {}", keystorePath);
        } else {
            // Dev/local fallback only — logs a loud warning so it is never silently used in prod.
            log.warn("JWT_KEYSTORE_PATH not set — generating ephemeral RSA key pair. " +
                     "Set app.jwt.keystore.path for persistent keys (required before production).");
            var gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            keyPair = gen.generateKeyPair();
        }
    }

    public PrivateKey getPrivateKey() { return keyPair.getPrivate(); }
    public PublicKey  getPublicKey()  { return keyPair.getPublic(); }

    private KeyPair loadFromKeystore() throws Exception {
        var ks = KeyStore.getInstance("PKCS12");
        try (var fis = new FileInputStream(keystorePath)) {
            ks.load(fis, keystorePassword.toCharArray());
        }
        var privateKey = (PrivateKey) ks.getKey(keystoreAlias, keystorePassword.toCharArray());
        var cert = ks.getCertificate(keystoreAlias);
        if (privateKey == null || cert == null) {
            throw new IllegalStateException("Keystore alias '" + keystoreAlias + "' not found in " + keystorePath);
        }
        return new KeyPair(cert.getPublicKey(), privateKey);
    }
}
