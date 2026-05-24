package com.demo.app.hiring.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hiring_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HiringRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID requesterId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    private String roleType;

    @Column(nullable = false, length = 200)
    private String department;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String urgency = "MEDIUM";

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column
    private UUID jobPostingId;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
