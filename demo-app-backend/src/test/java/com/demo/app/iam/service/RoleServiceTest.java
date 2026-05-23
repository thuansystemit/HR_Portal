package com.demo.app.iam.service;

import com.demo.app.iam.dto.CreateRoleRequest;
import com.demo.app.iam.dto.UpdateRoleRequest;
import com.demo.app.iam.entity.Permission;
import com.demo.app.iam.entity.Role;
import com.demo.app.iam.entity.RolePermission;
import com.demo.app.iam.repository.PermissionRepository;
import com.demo.app.iam.repository.RolePermissionRepository;
import com.demo.app.iam.repository.RoleRepository;
import com.demo.app.iam.repository.UserRoleRepository;
import com.demo.app.platform.exception.BuiltInRoleException;
import com.demo.app.platform.exception.BusinessRuleException;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock RoleRepository roleRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock RolePermissionRepository rolePermissionRepository;
    @Mock UserRoleRepository userRoleRepository;

    @InjectMocks
    RoleService roleService;

    private final UUID ROLE_ID = UUID.randomUUID();
    private final UUID PERM_ID = UUID.randomUUID();

    @Test
    void list_returnsPaged() {
        var role = buildRole(false);
        when(roleRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(role)));
        when(rolePermissionRepository.findByRoleId(ROLE_ID)).thenReturn(List.of());
        when(permissionRepository.findAllById(any())).thenReturn(List.of());
        when(userRoleRepository.countByRoleId(ROLE_ID)).thenReturn(0L);

        var result = roleService.list(0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void create_succeeds_whenNameAvailable() {
        var request = new CreateRoleRequest("Developer", "Dev role", Set.of(PERM_ID));
        var role = buildRole(false);
        var perm = Permission.builder().id(PERM_ID).code("docs.view").build();
        var rp = RolePermission.builder().roleId(ROLE_ID).permissionId(PERM_ID).build();

        when(roleRepository.existsByNameIgnoreCase("Developer")).thenReturn(false);
        when(roleRepository.save(any())).thenReturn(role);
        when(rolePermissionRepository.save(any())).thenReturn(rp);
        when(rolePermissionRepository.findByRoleId(ROLE_ID)).thenReturn(List.of(rp));
        when(permissionRepository.findAllById(any())).thenReturn(List.of(perm));
        when(userRoleRepository.countByRoleId(ROLE_ID)).thenReturn(0L);

        var result = roleService.create(request);

        assertThat(result.id()).isEqualTo(ROLE_ID);
        assertThat(result.permissionCodes()).contains("docs.view");
        verify(roleRepository).save(any());
    }

    @Test
    void create_throws_whenNameExists() {
        var request = new CreateRoleRequest("Existing", "desc", Set.of());
        when(roleRepository.existsByNameIgnoreCase("Existing")).thenReturn(true);

        assertThatThrownBy(() -> roleService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Role name already in use");

        verify(roleRepository, never()).save(any());
    }

    @Test
    void findById_returnsResponse_whenFound() {
        var role = buildRole(false);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.findByRoleId(ROLE_ID)).thenReturn(List.of());
        when(permissionRepository.findAllById(any())).thenReturn(List.of());
        when(userRoleRepository.countByRoleId(ROLE_ID)).thenReturn(2L);

        var result = roleService.findById(ROLE_ID);

        assertThat(result.id()).isEqualTo(ROLE_ID);
        assertThat(result.userCount()).isEqualTo(2L);
    }

    @Test
    void findById_throws_whenNotFound() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.findById(ROLE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_succeeds_whenNotBuiltin() {
        var role = buildRole(false);
        var request = new UpdateRoleRequest("Updated", "new desc", Set.of(PERM_ID));
        var perm = Permission.builder().id(PERM_ID).code("users.view").build();
        var rp = RolePermission.builder().roleId(ROLE_ID).permissionId(PERM_ID).build();

        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(roleRepository.save(any())).thenReturn(role);
        when(rolePermissionRepository.save(any())).thenReturn(rp);
        when(rolePermissionRepository.findByRoleId(ROLE_ID)).thenReturn(List.of(rp));
        when(permissionRepository.findAllById(any())).thenReturn(List.of(perm));
        when(userRoleRepository.countByRoleId(ROLE_ID)).thenReturn(1L);

        var result = roleService.update(ROLE_ID, request);

        assertThat(result.name()).isEqualTo("Updated");
        verify(rolePermissionRepository).deleteByRoleId(ROLE_ID);
    }

    @Test
    void update_throws_whenBuiltin() {
        var role = buildRole(true);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> roleService.update(ROLE_ID,
                new UpdateRoleRequest("X", "X", Set.of())))
                .isInstanceOf(BuiltInRoleException.class);
    }

    @Test
    void update_throws_whenNotFound() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.update(ROLE_ID,
                new UpdateRoleRequest("X", "X", Set.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_succeeds_whenNoUsers() {
        var role = buildRole(false);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(userRoleRepository.countByRoleId(ROLE_ID)).thenReturn(0L);

        roleService.delete(ROLE_ID);

        verify(rolePermissionRepository).deleteByRoleId(ROLE_ID);
        verify(roleRepository).delete(role);
    }

    @Test
    void delete_throws_whenBuiltin() {
        var role = buildRole(true);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> roleService.delete(ROLE_ID))
                .isInstanceOf(BuiltInRoleException.class);

        verify(roleRepository, never()).delete(any());
    }

    @Test
    void delete_throws_whenHasAssignedUsers() {
        var role = buildRole(false);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(userRoleRepository.countByRoleId(ROLE_ID)).thenReturn(3L);

        assertThatThrownBy(() -> roleService.delete(ROLE_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("assigned users");

        verify(roleRepository, never()).delete(any());
    }

    @Test
    void delete_throws_whenNotFound() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.delete(ROLE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_skipsPermissions_whenPermissionIdsEmpty() {
        var request = new CreateRoleRequest("Viewer", "Viewer role", Set.of());
        var role = buildRole(false);

        when(roleRepository.existsByNameIgnoreCase("Viewer")).thenReturn(false);
        when(roleRepository.save(any())).thenReturn(role);
        when(rolePermissionRepository.findByRoleId(ROLE_ID)).thenReturn(List.of());
        when(permissionRepository.findAllById(any())).thenReturn(List.of());
        when(userRoleRepository.countByRoleId(ROLE_ID)).thenReturn(0L);

        var result = roleService.create(request);

        assertThat(result.id()).isEqualTo(ROLE_ID);
        // No permissions should be saved since the set is empty
        verify(rolePermissionRepository, never()).save(any());
    }

    @Test
    void create_skipsPermissions_whenPermissionIdsNull() {
        var request = new CreateRoleRequest("Viewer", "Viewer role", null);
        var role = buildRole(false);

        when(roleRepository.existsByNameIgnoreCase("Viewer")).thenReturn(false);
        when(roleRepository.save(any())).thenReturn(role);
        when(rolePermissionRepository.findByRoleId(ROLE_ID)).thenReturn(List.of());
        when(permissionRepository.findAllById(any())).thenReturn(List.of());
        when(userRoleRepository.countByRoleId(ROLE_ID)).thenReturn(0L);

        var result = roleService.create(request);

        assertThat(result.id()).isEqualTo(ROLE_ID);
        verify(rolePermissionRepository, never()).save(any());
    }

    private Role buildRole(boolean builtin) {
        return Role.builder()
                .id(ROLE_ID)
                .name("Developer")
                .description("Dev role")
                .isBuiltin(builtin)
                .createdAt(Instant.now())
                .build();
    }
}
