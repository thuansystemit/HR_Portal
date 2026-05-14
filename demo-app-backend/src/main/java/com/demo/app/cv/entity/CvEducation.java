package com.demo.app.cv.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cv_educations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvEducation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID cvCandidateId;

    @Column(nullable = false)
    private short sortOrder;

    @Column(nullable = false, length = 400)
    private String institution;

    @Column(nullable = false, length = 200)
    private String degree;

    @Column(length = 300)
    private String fieldOfStudy;

    private Short startYear;

    private Short endYear;

    @Column(precision = 4, scale = 2)
    private BigDecimal gpa;

    @Column(length = 300)
    private String honors;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    @PrePersist
    void onCreate() { this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
