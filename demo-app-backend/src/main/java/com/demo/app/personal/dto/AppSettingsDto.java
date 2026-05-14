package com.demo.app.personal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AppSettingsDto(
        @NotBlank String theme,
        @NotBlank String language,
        @NotBlank String dateFormat,
        @Min(5) int defaultPageSize,
        boolean notifEmail,
        boolean notifPush,
        boolean notifDesktop
) {}
