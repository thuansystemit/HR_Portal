package com.demo.app.iam.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        boolean isBuiltin,
        List<String> permissionCodes,
        long userCount,
        Instant createdAt
) {}
