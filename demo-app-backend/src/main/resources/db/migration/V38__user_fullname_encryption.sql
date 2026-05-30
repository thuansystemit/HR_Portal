-- SC-28: Widen users.full_name to hold AES-256-GCM encrypted values.
--
-- Encrypted format: ENC:<base64(12-byte-IV || ciphertext || 16-byte-GCM-tag)>
-- For a 120-char full name: 12 + 120 + 16 = 148 raw bytes → 200 base64 chars + "ENC:" prefix = 204 chars.
-- VARCHAR(300) provides safe headroom.
--
-- Existing plaintext rows are NOT touched; PiiEncryptionConverter handles them transparently
-- via the migration-safe ENC: prefix fallback (returns plaintext when prefix is absent).

ALTER TABLE users ALTER COLUMN full_name TYPE VARCHAR(300);
