package com.demo.app.cv.entity;

import com.demo.app.platform.security.encryption.PiiEncryptionConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "cv_candidates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private UUID documentCategoryId;

    @Column(nullable = false, length = 300)
    private String fullName;

    @Convert(converter = PiiEncryptionConverter.class)
    @Column(length = 600)
    private String email;

    @Convert(converter = PiiEncryptionConverter.class)
    @Column(length = 100)
    private String phone;

    @Convert(converter = PiiEncryptionConverter.class)
    @Column(length = 400)
    private String city;

    @Column(length = 2)
    private String country;

    @Column(length = 500)
    private String linkedinUrl;

    @Column(length = 500)
    private String githubUrl;

    @Column(length = 500)
    private String portfolioUrl;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> toolsAndFrameworks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> softSkills;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> projects;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> publications;

    @Column(nullable = false, length = 6)
    private String confidenceOverall;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> lowConfidenceFields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> missingFields;

    @Column(length = 2)
    private String rawLanguage;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private CandidateHiringStatus hiringStatus = CandidateHiringStatus.AVAILABLE;

    @Builder.Default
    @Column(nullable = false)
    private Instant extractedAt = Instant.now();

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() { this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
