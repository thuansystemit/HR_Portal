package com.demo.app.platform.security.encryption;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesFieldEncryptionServiceTest {

    // 64 hex chars = 32 bytes = AES-256
    private static final String TEST_KEY = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

    private final AesFieldEncryptionService service = new AesFieldEncryptionService(TEST_KEY);

    @Test
    void encrypt_producesEncPrefix() {
        var result = service.encrypt("alice@example.com");
        assertThat(result).startsWith(AesFieldEncryptionService.ENC_PREFIX);
    }

    @Test
    void roundTrip_decryptReturnsOriginal() {
        String original = "alice@example.com";
        String encrypted = service.encrypt(original);
        assertThat(service.decrypt(encrypted)).isEqualTo(original);
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime() {
        String c1 = service.encrypt("same@example.com");
        String c2 = service.encrypt("same@example.com");
        assertThat(c1).isNotEqualTo(c2); // different IVs
    }

    @Test
    void decrypt_returnsPlaintext_whenNoEncPrefix() {
        // Migration-safe: legacy plaintext rows returned as-is
        assertThat(service.decrypt("legacy-plaintext")).isEqualTo("legacy-plaintext");
    }

    @Test
    void encrypt_returnsNull_forNullInput() {
        assertThat(service.encrypt(null)).isNull();
    }

    @Test
    void decrypt_returnsNull_forNullInput() {
        assertThat(service.decrypt(null)).isNull();
    }

    @Test
    void roundTrip_handlesLongEmail() {
        String longEmail = "a".repeat(255) + "@example.com";
        assertThat(service.decrypt(service.encrypt(longEmail))).isEqualTo(longEmail);
    }

    @Test
    void roundTrip_handlesUnicodeCity() {
        String city = "Hà Nội";
        assertThat(service.decrypt(service.encrypt(city))).isEqualTo(city);
    }

    @Test
    void constructor_ephemeralKey_whenBlank() {
        // Should not throw — generates ephemeral key
        var ephemeral = new AesFieldEncryptionService("");
        String encrypted = ephemeral.encrypt("test");
        assertThat(encrypted).startsWith(AesFieldEncryptionService.ENC_PREFIX);
    }

    @Test
    void constructor_throws_whenKeyTooShort() {
        assertThatThrownBy(() -> new AesFieldEncryptionService("aabb"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AES-256");
    }

    @Test
    void decrypt_throws_whenCiphertextTampered() {
        String encrypted = service.encrypt("sensitive");
        String tampered = AesFieldEncryptionService.ENC_PREFIX + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }
}
