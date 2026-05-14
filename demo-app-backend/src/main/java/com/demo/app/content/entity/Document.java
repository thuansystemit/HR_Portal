package com.demo.app.content.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 127)
    private String mimeType = "application/octet-stream";

    @Column(nullable = false)
    private long sizeBytes = 0;

    @Column(nullable = false, length = 512)
    private String storageKey;

    private UUID uploadedBy;

    @Column(nullable = false, length = 20)
    private String uploadStatus = "pending";

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    private Instant deletedAt;

    @Column(length = 20)
    private String extractionStatus;

    @Column(columnDefinition = "TEXT")
    private String extractionError;
}
