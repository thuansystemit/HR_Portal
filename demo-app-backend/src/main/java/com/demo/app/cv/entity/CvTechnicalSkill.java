package com.demo.app.cv.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cv_technical_skills")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvTechnicalSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID cvCandidateId;

    @Column(nullable = false, length = 200)
    private String skillName;

    @Column(insertable = false, updatable = false, length = 200)
    private String skillNameLower;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    @PrePersist
    void onCreate() { this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
