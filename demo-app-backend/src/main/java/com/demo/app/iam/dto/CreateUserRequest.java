package com.demo.app.iam.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull UUID roleId,
        @NotBlank @Size(min = 6) String password,
        String status
) {}
