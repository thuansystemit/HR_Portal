package com.demo.app.knowledge.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "knowledge_entity_sources")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeEntitySource {

    @EmbeddedId
    private KnowledgeEntitySourceId id;

    @Column(columnDefinition = "TEXT")
    private String excerpt;

    private Integer pageNumber;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
