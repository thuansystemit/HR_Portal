package com.demo.app.iam.entity;

import com.demo.app.platform.security.encryption.PiiEncryptionConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "credentials")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private int failedAttempts = 0;

    private Instant lockedUntil;

    @Builder.Default
    @Column(nullable = false)
    private boolean mfaEnabled = false;

    // SC-28: TOTP secret encrypted at rest; PiiEncryptionConverter applies AES-256-GCM
    @Convert(converter = PiiEncryptionConverter.class)
    private String mfaSecret;

    @Column(length = 20)
    private String mfaMethod;

    private Instant mfaEnrolledAt;

    // Stored as JSONB array; each entry is SHA-256(plaintext code), single-use
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mfa_backup_codes", columnDefinition = "jsonb")
    private List<String> mfaBackupCodes;

    private Instant previousLoginAt;

    private Instant lastLoginAt;

    // IA-5(1)(d): timestamp when the password was last set — used to enforce max-age rotation
    @Builder.Default
    @Column(nullable = false)
    private Instant passwordChangedAt = Instant.now();

    // IA-5(1)(f): admin-provisioned accounts must change their initial password on first login
    @Builder.Default
    @Column(nullable = false)
    private boolean mustChangePassword = false;

    @Builder.Default
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
