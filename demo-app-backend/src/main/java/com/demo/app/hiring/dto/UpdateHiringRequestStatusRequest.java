package com.demo.app.hiring.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record UpdateHiringRequestStatusRequest(
        @NotBlank String status,
        UUID jobPostingId
) {}
