package com.demo.app.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateRoleRequest(
        @NotBlank @Size(min = 2, max = 80) String name,
        @Size(max = 255) String description,
        Set<UUID> permissionIds
) {}
