package com.demo.app.hiring.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cv_shares")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvShare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID hiringRequestId;

    @Column(nullable = false)
    private UUID cvCandidateId;

    @Column(nullable = false)
    private UUID sharedBy;

    @Column(nullable = false)
    private UUID sharedWith;

    @Column(length = 20)
    private String impression;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Builder.Default
    @Column(name = "shared_at", nullable = false, updatable = false)
    private Instant sharedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @PrePersist
    void onCreate() {
        if (this.sharedAt == null) {
            this.sharedAt = Instant.now();
        }
    }
}
