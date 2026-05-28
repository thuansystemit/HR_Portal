package com.demo.app.platform.security.encryption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM field-level encryption (SC-28).
 * Output format stored in DB: {@code ENC:<base64(12-byte-IV || ciphertext+tag)>}
 * <p>
 * The {@code ENC:} prefix enables safe zero-downtime migration: legacy plaintext rows
 * are returned as-is until re-encrypted by the migration job.
 * <p>
 * Key source: {@code app.encryption.pii-key} (64 hex chars = 32 bytes).
 * If unset, an ephemeral random key is generated — only suitable for dev/test.
 * Set a persistent key before storing real PII.
 */
@Service
@Slf4j
public class AesFieldEncryptionService implements FieldEncryptionService {

    static final String ENC_PREFIX = "ENC:";
    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom rng = new SecureRandom();

    public AesFieldEncryptionService(@Value("${app.encryption.pii-key:}") String hexKey) {
        if (hexKey == null || hexKey.isBlank()) {
            log.warn("app.encryption.pii-key not configured — generating ephemeral AES-256 key. " +
                     "Set this env var for persistent PII encryption (required before production).");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secretKey = new SecretKeySpec(random, "AES");
        } else {
            byte[] keyBytes = HexFormat.of().parseHex(hexKey);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException(
                        "app.encryption.pii-key must be 64 hex chars (32 bytes / AES-256). Got " +
                        keyBytes.length + " bytes.");
            }
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        }
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            rng.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] combined = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, combined, IV_BYTES, ciphertext.length);

            return ENC_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("PII field encryption failed", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        // Migration-safe: plaintext rows (no ENC: prefix) are returned as-is
        if (!ciphertext.startsWith(ENC_PREFIX)) return ciphertext;
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext.substring(ENC_PREFIX.length()));
            byte[] iv = Arrays.copyOf(combined, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("PII field decryption failed", e);
        }
    }
}
