package com.demo.app.content.dto;

import com.demo.app.content.entity.DocumentType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String description,
        int documentCount,
        List<CategoryPermissionDto> permissions,
        Instant createdAt,
        DocumentType documentType,
        boolean llmExtraction
) {}
