package com.demo.app.platform.security.encryption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiEncryptionConverterTest {

    private static final String TEST_KEY = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

    private PiiEncryptionConverter converter;

    @BeforeEach
    void setUp() {
        PiiEncryptionConverter.init(new AesFieldEncryptionService(TEST_KEY));
        converter = new PiiEncryptionConverter();
    }

    @Test
    void convertToDatabaseColumn_encryptsValue() {
        String result = converter.convertToDatabaseColumn("alice@example.com");
        assertThat(result).startsWith(AesFieldEncryptionService.ENC_PREFIX);
    }

    @Test
    void convertToEntityAttribute_decryptsValue() {
        String encrypted = converter.convertToDatabaseColumn("alice@example.com");
        assertThat(converter.convertToEntityAttribute(encrypted)).isEqualTo("alice@example.com");
    }

    @Test
    void convertToDatabaseColumn_returnsNull_forNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_returnsNull_forNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_returnsPlaintext_whenNoPrefix() {
        assertThat(converter.convertToEntityAttribute("legacy-plain")).isEqualTo("legacy-plain");
    }

    @Test
    void converter_isPassthrough_whenServiceNotInitialized() {
        PiiEncryptionConverter.init(null);
        var uninit = new PiiEncryptionConverter();
        assertThat(uninit.convertToDatabaseColumn("plain")).isEqualTo("plain");
        assertThat(uninit.convertToEntityAttribute("plain")).isEqualTo("plain");
        // restore for other tests
        PiiEncryptionConverter.init(new AesFieldEncryptionService(TEST_KEY));
    }
}
