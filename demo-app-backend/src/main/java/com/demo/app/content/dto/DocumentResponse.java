package com.demo.app.content.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID categoryId,
        String name,
        String mimeType,
        long sizeBytes,
        UUID uploadedBy,
        Instant uploadedAt
) {}
