package com.demo.app.knowledge.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record KnowledgeGraphResponse(
        List<NodeDto> nodes,
        List<EdgeDto> edges
) {
    public record NodeDto(
            UUID id,
            String name,
            String entityType,
            Map<String, Object> properties
    ) {}

    public record EdgeDto(
            UUID id,
            UUID source,
            UUID target,
            String relationType,
            double weight
    ) {}
}
