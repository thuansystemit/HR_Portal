package com.demo.app.iam.entity;

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

    private String mfaSecret;

    @Column(length = 20)
    private String mfaMethod;

    private Instant mfaEnrolledAt;

    // Stored as JSONB array; each entry is SHA-256(plaintext code), single-use
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mfa_backup_codes", columnDefinition = "jsonb")
    private List<String> mfaBackupCodes;

    @Builder.Default
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
