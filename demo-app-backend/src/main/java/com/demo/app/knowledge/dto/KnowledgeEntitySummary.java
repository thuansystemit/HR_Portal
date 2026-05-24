package com.demo.app.knowledge.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KnowledgeEntitySummary(
        UUID id,
        UUID documentId,
        String entityType,
        String name,
        List<String> aliases,
        Instant createdAt
) {}
