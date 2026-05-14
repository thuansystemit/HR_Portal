package com.demo.app.cv.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cv_certifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvCertification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID cvCandidateId;

    @Column(nullable = false, length = 400)
    private String name;

    @Column(length = 300)
    private String issuer;

    private LocalDate issuedDate;

    private LocalDate expiryDate;

    @Column(length = 200)
    private String credentialId;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    @PrePersist
    void onCreate() { this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
