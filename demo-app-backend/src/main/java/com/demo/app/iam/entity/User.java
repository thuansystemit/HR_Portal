package com.demo.app.iam.entity;

import com.demo.app.platform.security.encryption.PiiEncryptionConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // SC-28: full name is PII — encrypted at rest with AES-256-GCM
    @Convert(converter = PiiEncryptionConverter.class)
    @Column(nullable = false, length = 300)
    private String fullName;

    @Column(nullable = false, length = 254, unique = true)
    private String email;

    @Column(nullable = false, length = 20)
    private String status = "active";

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;
    private Instant deletedAt;

    @PrePersist
    void onCreate() { this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
