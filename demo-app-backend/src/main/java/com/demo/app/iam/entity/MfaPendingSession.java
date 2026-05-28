package com.demo.app.iam.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mfa_pending_sessions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MfaPendingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String challengeToken;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(length = 45)
    private String ipAddress;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
