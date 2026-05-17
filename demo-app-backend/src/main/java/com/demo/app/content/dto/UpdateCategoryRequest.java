package com.demo.app.content.dto;

import com.demo.app.content.entity.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateCategoryRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @Size(max = 500) String description,
        @NotNull DocumentType documentType,
        Boolean llmExtraction,
        List<PermissionEntry> permissions
) {
    public record PermissionEntry(
            UUID roleId,
            boolean canView,
            boolean canUpload,
            boolean canDelete
    ) {}
}
