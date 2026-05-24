package com.demo.app.iam.service;

import com.demo.app.iam.dto.CreateUserRequest;
import com.demo.app.iam.dto.PagedResponse;
import com.demo.app.iam.dto.UpdateUserRequest;
import com.demo.app.iam.dto.UserResponse;
import com.demo.app.iam.entity.Credential;
import com.demo.app.iam.entity.User;
import com.demo.app.iam.entity.UserRole;
import com.demo.app.iam.repository.*;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionCacheService permissionCacheService;

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> list(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var p = userRepository.findAllActive(pageable);
        var content = p.getContent().stream().map(this::mapToResponse).toList();
        return new PagedResponse<>(content, page, size, p.getTotalElements(), p.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listByRoleName(String roleName) {
        return roleRepository.findByName(roleName)
                .map(role -> userRepository.findActiveByRoleId(role.getId())
                        .stream().map(this::mapToResponse).toList())
                .orElse(List.of());
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(request.email())) {
            throw new ConflictException("Email already in use: " + request.email());
        }
        roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", request.roleId()));

        var status = request.status() != null ? request.status() : "active";
        var user = User.builder()
                .fullName(request.fullName())
                .email(request.email().toLowerCase())
                .status(status)
                .build();
        user = userRepository.save(user);

        credentialRepository.save(Credential.builder()
                .userId(user.getId())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build());

        userRoleRepository.save(UserRole.builder()
                .userId(user.getId())
                .roleId(request.roleId())
                .build());

        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        var user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return mapToResponse(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        var user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", request.roleId()));

        user.setFullName(request.fullName());
        user.setStatus(request.status());
        userRepository.save(user);

        var existing = userRoleRepository.findByUserIdAndRoleId(id, request.roleId());
        if (existing.isEmpty()) {
            userRoleRepository.deleteByUserId(id);
            userRoleRepository.save(UserRole.builder()
                    .userId(id).roleId(request.roleId()).build());
            permissionCacheService.evict(id);
        }

        return mapToResponse(user);
    }

    @Transactional
    public void delete(UUID id) {
        var user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
        permissionCacheService.evict(id);
    }

    private UserResponse mapToResponse(User user) {
        var roles = userRoleRepository.findByUserId(user.getId());
        UUID roleId = null;
        String roleName = null;
        if (!roles.isEmpty()) {
            roleId = roles.get(0).getRoleId();
            var finalRoleId = roleId;
            roleName = roleRepository.findById(finalRoleId).map(r -> r.getName()).orElse(null);
        }
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(),
                roleId, roleName, user.getStatus(), user.getCreatedAt());
    }
}
