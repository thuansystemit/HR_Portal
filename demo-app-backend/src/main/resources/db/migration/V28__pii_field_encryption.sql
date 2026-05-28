-- SC-28: Expand PII columns on cv_candidates to hold AES-256-GCM encrypted values.
--
-- Encrypted format: ENC:<base64(12-byte-IV || ciphertext || 16-byte-GCM-tag)>
-- For a 320-char plaintext email: 12 + 320 + 16 = 348 raw bytes → 464 base64 chars + 4 prefix = 468.
-- VARCHAR(600) provides safe headroom.
--
-- Existing plaintext rows are NOT touched here; the PiiEncryptionConverter handles them
-- transparently via the migration-safe fallback (returns plaintext if ENC: prefix absent).

ALTER TABLE cv_candidates ALTER COLUMN email  TYPE VARCHAR(600);
ALTER TABLE cv_candidates ALTER COLUMN phone  TYPE VARCHAR(100);
ALTER TABLE cv_candidates ALTER COLUMN city   TYPE VARCHAR(400);
