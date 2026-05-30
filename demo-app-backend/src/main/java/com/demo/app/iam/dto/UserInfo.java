package com.demo.app.iam.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserInfo(
        UUID id,
        String fullName,
        String email,
        UUID roleId,
        String roleName,
        Set<String> permissions,
        Instant previousLoginAt   // AC-9: shown to the user upon successful logon
) {}
