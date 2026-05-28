-- FedRAMP P0-1: FIPS 140-2 cryptography migration
-- Widen password hash columns for PBKDF2 (longer than BCrypt's 60 chars)
-- and prefix existing BCrypt hashes so DelegatingPasswordEncoder recognises them.

ALTER TABLE credentials
    ALTER COLUMN password_hash TYPE VARCHAR(255);

ALTER TABLE password_history
    ALTER COLUMN password_hash TYPE VARCHAR(255);

-- Tag existing raw BCrypt hashes with the {bcrypt} encoder ID.
-- DelegatingPasswordEncoder uses this prefix to route to the correct verifier.
-- After users log in, Spring Security re-encodes to {pbkdf2@sha256} automatically.
UPDATE credentials
SET password_hash = '{bcrypt}' || password_hash
WHERE password_hash NOT LIKE '{%}%';

UPDATE password_history
SET password_hash = '{bcrypt}' || password_hash
WHERE password_hash NOT LIKE '{%}%';
