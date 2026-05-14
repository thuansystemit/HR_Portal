package com.demo.app.iam.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        UUID roleId,
        String roleName,
        String status,
        Instant createdAt
) {}
