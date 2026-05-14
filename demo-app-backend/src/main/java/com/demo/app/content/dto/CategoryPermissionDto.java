package com.demo.app.content.dto;

import java.util.UUID;

public record CategoryPermissionDto(
        UUID roleId,
        String roleName,
        boolean canView,
        boolean canUpload,
        boolean canDelete
) {}
