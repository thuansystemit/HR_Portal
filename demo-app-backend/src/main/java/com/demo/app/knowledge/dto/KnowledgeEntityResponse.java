package com.demo.app.knowledge.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record KnowledgeEntityResponse(
        UUID id,
        UUID documentId,
        String entityType,
        String name,
        List<String> aliases,
        Map<String, Object> properties,
        Instant createdAt,
        List<RelationshipSummary> relationships,
        List<SourceSummary> sources
) {
    public record RelationshipSummary(
            UUID id,
            UUID otherEntityId,
            String otherEntityName,
            String direction,
            String relationType,
            double weight
    ) {}

    public record SourceSummary(
            UUID documentId,
            String excerpt,
            Integer pageNumber
    ) {}
}
