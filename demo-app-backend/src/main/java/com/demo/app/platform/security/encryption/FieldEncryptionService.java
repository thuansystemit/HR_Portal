package com.demo.app.platform.security.encryption;

/**
 * Abstraction over field-level encryption backends (local AES-256-GCM, AWS KMS).
 * SC-28: protects PII columns so that raw DB access does not expose plaintext.
 */
public interface FieldEncryptionService {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
