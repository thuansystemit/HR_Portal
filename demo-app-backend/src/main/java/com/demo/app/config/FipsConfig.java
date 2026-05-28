package com.demo.app.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.springframework.context.annotation.Configuration;

import java.security.Security;

@Configuration
@Slf4j
public class FipsConfig {

    @PostConstruct
    public void registerFipsProvider() {
        if (Security.getProvider("BCFIPS") == null) {
            Security.insertProviderAt(new BouncyCastleFipsProvider(), 1);
            log.info("Bouncy Castle FIPS provider registered (FIPS 140-2)");
        }
    }
}
