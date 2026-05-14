package com.demo.app.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateUserRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotNull UUID roleId,
        @NotBlank String status
) {}
