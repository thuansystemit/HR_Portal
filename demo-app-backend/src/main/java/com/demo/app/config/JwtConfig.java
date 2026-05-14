package com.demo.app.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.security.*;

@Configuration
@Getter
public class JwtConfig {

    @Value("${app.jwt.access-expiry-seconds}")
    private long accessExpirySeconds;

    @Value("${app.jwt.refresh-expiry-seconds}")
    private long refreshExpirySeconds;

    private final KeyPair keyPair;

    public JwtConfig() throws NoSuchAlgorithmException {
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        this.keyPair = gen.generateKeyPair();
    }

    public PrivateKey getPrivateKey() { return keyPair.getPrivate(); }
    public PublicKey  getPublicKey()  { return keyPair.getPublic(); }
}
