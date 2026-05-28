package com.demo.app.platform.security.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter that transparently encrypts/decrypts PII string columns (SC-28).
 *
 * The converter delegates to a static {@link FieldEncryptionService} instance wired by
 * {@link com.demo.app.config.EncryptionConfig} at application startup. Using a static
 * holder avoids the Hibernate/Spring DI bootstrapping race where Hibernate instantiates
 * converters before the Spring context is fully ready.
 *
 * Usage: {@code @Convert(converter = PiiEncryptionConverter.class)}
 */
@Converter
public class PiiEncryptionConverter implements AttributeConverter<String, String> {

    private static volatile FieldEncryptionService encryptionService;

    public static void init(FieldEncryptionService service) {
        encryptionService = service;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || encryptionService == null) return attribute;
        return encryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || encryptionService == null) return dbData;
        return encryptionService.decrypt(dbData);
    }
}
