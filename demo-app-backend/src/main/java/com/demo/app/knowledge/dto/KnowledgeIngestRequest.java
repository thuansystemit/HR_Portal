package com.demo.app.knowledge.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record KnowledgeIngestRequest(
        @NotNull UUID documentId,
        @NotNull UUID categoryId,
        String extractionStatus,
        List<String> guardrailWarnings,
        String title,
        String summary,
        String documentType,
        List<TechEntityDto> technologies,
        List<ConceptEntityDto> concepts,
        List<RelationshipDto> relationships
) {
    public record TechEntityDto(
            String name,
            String version,
            String category,
            List<String> aliases
    ) {}

    public record ConceptEntityDto(
            String name,
            String definition,
            List<String> relatedConcepts
    ) {}

    public record RelationshipDto(
            String source,
            String target,
            String relationType,
            Double weight
    ) {}
}
