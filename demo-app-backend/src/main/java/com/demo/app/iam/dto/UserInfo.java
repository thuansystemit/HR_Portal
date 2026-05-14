package com.demo.app.iam.dto;

import java.util.Set;
import java.util.UUID;

public record UserInfo(
        UUID id,
        String fullName,
        String email,
        UUID roleId,
        String roleName,
        Set<String> permissions
) {}
