package com.demo.app.iam.service;

import com.demo.app.iam.entity.Permission;
import com.demo.app.iam.entity.RolePermission;
import com.demo.app.iam.entity.UserRole;
import com.demo.app.iam.repository.PermissionRepository;
import com.demo.app.iam.repository.RolePermissionRepository;
import com.demo.app.iam.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionCacheServiceTest {

    @Mock UserRoleRepository userRoleRepository;
    @Mock RolePermissionRepository rolePermissionRepository;
    @Mock PermissionRepository permissionRepository;

    @InjectMocks
    PermissionCacheService permissionCacheService;

    private final UUID USER_ID = UUID.randomUUID();
    private final UUID ROLE_ID = UUID.randomUUID();
    private final UUID PERM_ID = UUID.randomUUID();

    @Test
    void loadPermissions_returnsCodesAndRoleId_whenRolesExist() {
        var userRole = UserRole.builder().userId(USER_ID).roleId(ROLE_ID).build();
        var rolePermission = RolePermission.builder().roleId(ROLE_ID).permissionId(PERM_ID).build();
        var permission = Permission.builder().id(PERM_ID).code("users.view").build();

        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(userRole));
        when(rolePermissionRepository.findByRoleId(ROLE_ID)).thenReturn(List.of(rolePermission));
        when(permissionRepository.findAllById(any())).thenReturn(List.of(permission));

        var result = permissionCacheService.loadPermissions(USER_ID);

        assertThat(result.roleId()).isEqualTo(ROLE_ID);
        assertThat(result.codes()).contains("users.view");
    }

    @Test
    void loadPermissions_returnsEmpty_whenNoRoles() {
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of());

        var result = permissionCacheService.loadPermissions(USER_ID);

        assertThat(result.roleId()).isNull();
        assertThat(result.codes()).isEmpty();
    }

    @Test
    void evict_executesWithoutError() {
        // evict is a cache eviction -- just verify the call completes without exception
        assertThatCode(() -> permissionCacheService.evict(USER_ID))
                .doesNotThrowAnyException();
    }
}
