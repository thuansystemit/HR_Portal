package com.demo.app.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class KnowledgeEntitySourceId implements Serializable {

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private UUID documentId;
}
