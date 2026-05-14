package com.demo.app.iam.service;

import com.demo.app.iam.dto.CreateRoleRequest;
import com.demo.app.iam.dto.PagedResponse;
import com.demo.app.iam.dto.RoleResponse;
import com.demo.app.iam.dto.UpdateRoleRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;

    @Transactional(readOnly = true)
    public PagedResponse<RoleResponse> list(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        var p = roleRepository.findAll(pageable);
        var content = p.getContent().stream().map(this::toResponse).toList();
        return new PagedResponse<>(content, page, size, p.getTotalElements(), p.getTotalPages());
    }

    @Transactional
    public RoleResponse create(CreateRoleRequest request) {
        if (roleRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Role name already in use: " + request.name());
        }
        var role = Role.builder().name(request.name()).description(request.description()).build();
        role = roleRepository.save(role);
        savePermissions(role.getId(), request.permissionIds());
        return toResponse(role);
    }

    @Transactional(readOnly = true)
    public RoleResponse findById(UUID id) {
        return roleRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));
    }

    @Transactional
    public RoleResponse update(UUID id, UpdateRoleRequest request) {
        var role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));
        if (role.isBuiltin()) throw new BuiltInRoleException();

        role.setName(request.name());
        role.setDescription(request.description());
        roleRepository.save(role);

        rolePermissionRepository.deleteByRoleId(id);
        savePermissions(id, request.permissionIds());
        return toResponse(role);
    }

    @Transactional
    public void delete(UUID id) {
        var role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));
        if (role.isBuiltin()) throw new BuiltInRoleException();
        if (userRoleRepository.countByRoleId(id) > 0) {
            throw new BusinessRuleException("Cannot delete a role that has assigned users");
        }
        rolePermissionRepository.deleteByRoleId(id);
        roleRepository.delete(role);
    }

    private void savePermissions(UUID roleId, Set<UUID> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) return;
        permissionIds.stream()
                .map(pid -> RolePermission.builder().roleId(roleId).permissionId(pid).build())
                .forEach(rolePermissionRepository::save);
    }

    private RoleResponse toResponse(Role role) {
        var perms = rolePermissionRepository.findByRoleId(role.getId());
        var permIds = perms.stream().map(rp -> rp.getPermissionId()).toList();
        var codes = permissionRepository.findAllById(permIds).stream()
                .map(p -> p.getCode()).toList();
        var userCount = userRoleRepository.countByRoleId(role.getId());
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(),
                role.isBuiltin(), codes, userCount, role.getCreatedAt());
    }
}
