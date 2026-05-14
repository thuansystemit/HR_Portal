package com.demo.app.iam.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 512)
    private String userAgent;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
