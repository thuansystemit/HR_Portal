package com.demo.app.iam.service;

import com.demo.app.iam.repository.PermissionRepository;
import com.demo.app.iam.repository.RolePermissionRepository;
import com.demo.app.iam.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;

    @Cacheable(value = "permissions", key = "#userId")
    @Transactional(readOnly = true)
    public PermissionSet loadPermissions(UUID userId) {
        var userRoles = userRoleRepository.findByUserId(userId);
        if (userRoles.isEmpty()) {
            return new PermissionSet(null, Set.of());
        }
        var roleId = userRoles.get(0).getRoleId();
        var rolePermissions = rolePermissionRepository.findByRoleId(roleId);
        var permissionIds = rolePermissions.stream()
                .map(rp -> rp.getPermissionId())
                .collect(Collectors.toSet());
        var codes = permissionRepository.findAllById(permissionIds).stream()
                .map(p -> p.getCode())
                .collect(Collectors.toSet());
        return new PermissionSet(roleId, codes);
    }

    @CacheEvict(value = "permissions", key = "#userId")
    public void evict(UUID userId) {}

    public record PermissionSet(UUID roleId, Set<String> codes) {}
}
