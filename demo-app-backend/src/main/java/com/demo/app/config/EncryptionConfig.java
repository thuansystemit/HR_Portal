package com.demo.app.config;

import com.demo.app.platform.security.encryption.FieldEncryptionService;
import com.demo.app.platform.security.encryption.PiiEncryptionConverter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Spring-managed {@link FieldEncryptionService} into
 * {@link PiiEncryptionConverter}'s static holder at startup.
 *
 * JPA converters are instantiated by Hibernate before the Spring context completes,
 * so constructor/field injection on the converter itself is unreliable.
 * This config class runs after all beans are ready and sets the static reference.
 */
@Configuration
@RequiredArgsConstructor
public class EncryptionConfig {

    private final FieldEncryptionService fieldEncryptionService;

    @PostConstruct
    public void init() {
        PiiEncryptionConverter.init(fieldEncryptionService);
    }
}
