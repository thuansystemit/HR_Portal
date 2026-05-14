package com.demo.app.cv.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "cv_work_experiences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvWorkExperience {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID cvCandidateId;

    @Column(nullable = false)
    private short sortOrder;

    @Column(nullable = false, length = 300)
    private String company;

    @Column(nullable = false, length = 300)
    private String title;

    private LocalDate startDate;

    @Column(length = 5)
    private String startDatePrecision;

    private LocalDate endDate;

    @Column(nullable = false)
    @Builder.Default
    private boolean isCurrent = false;

    @Column(length = 300)
    private String location;

    private Boolean isRemote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> responsibilities;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> achievements;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> technologies;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    @PrePersist
    void onCreate() { this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
