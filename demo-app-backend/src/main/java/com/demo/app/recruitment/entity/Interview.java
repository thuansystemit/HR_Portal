package com.demo.app.recruitment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interviews")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private Instant scheduledAt;

    @Column(length = 500)
    private String meetingLink;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private UUID createdBy;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
