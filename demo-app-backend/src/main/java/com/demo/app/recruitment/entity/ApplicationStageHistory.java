package com.demo.app.recruitment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_stage_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Column(length = 20)
    private String fromStage;

    @Column(nullable = false, length = 20)
    private String toStage;

    @Column(nullable = false)
    private UUID movedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant movedAt = Instant.now();
}
