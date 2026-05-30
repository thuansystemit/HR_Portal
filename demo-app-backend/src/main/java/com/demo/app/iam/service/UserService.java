package com.demo.app.iam.service;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.config.JwtConfig;
import com.demo.app.platform.metrics.SecurityEventRecorder;
import com.demo.app.iam.dto.AdminResetPasswordRequest;
import com.demo.app.iam.dto.CreateUserRequest;
import com.demo.app.iam.dto.LockStatusResponse;
import com.demo.app.iam.dto.PagedResponse;
import com.demo.app.iam.dto.UpdateUserRequest;
import com.demo.app.iam.dto.UserResponse;
import com.demo.app.iam.entity.Credential;
import com.demo.app.iam.entity.User;
import com.demo.app.iam.entity.UserRole;
import com.demo.app.iam.repository.*;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ForbiddenException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private final SessionActivityService sessionActivityService;
    private final TokenDenylistService tokenDenylistService;
    private final JwtConfig jwtConfig;
    private final AuditService auditService;
    private final SecurityEventRecorder securityEventRecorder;
    private final PasswordPolicyService passwordPolicyService;
    private final PasswordHistoryService passwordHistoryService;

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
    public UserResponse create(CreateUserRequest request, UUID actorId) {
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(request.email())) {
            throw new ConflictException("Email already in use: " + request.email());
        }
        roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", request.roleId()));

        passwordPolicyService.validate(request.password(), request.email(), request.fullName());

        var status = request.status() != null ? request.status() : "active";
        var user = User.builder()
                .fullName(request.fullName())
                .email(request.email().toLowerCase())
                .status(status)
                .build();
        user = userRepository.save(user);

        var encodedPassword = passwordEncoder.encode(request.password());
        // IA-5(1)(f): require immediate password change on first login for admin-provisioned accounts
        credentialRepository.save(Credential.builder()
                .userId(user.getId())
                .passwordHash(encodedPassword)
                .mustChangePassword(true)
                .build());
        passwordHistoryService.record(user.getId(), encodedPassword);

        userRoleRepository.save(UserRole.builder()
                .userId(user.getId())
                .roleId(request.roleId())
                .build());

        // AU-2: audit user creation so provisioning events appear in the audit trail
        auditService.log(actorId, "USER_CREATED", "User", user.getId(),
                null, Map.of("email", user.getEmail(), "status", status,
                        "roleId", request.roleId().toString()), "success");

        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        var user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return mapToResponse(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, UUID actorId) {
        // AC-5: no single user may unilaterally change their own role or account status
        if (id.equals(actorId)) {
            throw new ForbiddenException("AC-5: users may not modify their own account");
        }
        var user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", request.roleId()));

        String oldName   = user.getFullName();
        String oldStatus = user.getStatus();
        user.setFullName(request.fullName());
        user.setStatus(request.status());
        userRepository.save(user);

        var existing = userRoleRepository.findByUserIdAndRoleId(id, request.roleId());
        if (existing.isEmpty()) {
            // IA-11: capture old role before replacing so it appears in the audit record
            var currentRoles = userRoleRepository.findByUserId(id);
            UUID oldRoleId = currentRoles.isEmpty() ? null : currentRoles.get(0).getRoleId();

            userRoleRepository.deleteByUserId(id);
            userRoleRepository.save(UserRole.builder()
                    .userId(id).roleId(request.roleId()).build());
            permissionCacheService.evict(id);
            // IA-11: immediately invalidate all active sessions so the privilege change takes
            // effect at next login rather than after JWT expiry (up to 15 minutes later)
            revokeAllSessions(id);
            auditService.log(actorId, "USER_ROLE_CHANGED", "User", id,
                    oldRoleId != null ? Map.of("roleId", oldRoleId.toString()) : null,
                    Map.of("roleId", request.roleId().toString()), "success");
        }

        // AC-2: revoke all active sessions when deactivating
        if ("active".equals(oldStatus) && !"active".equals(request.status())) {
            revokeAllSessions(id);
            auditService.log(actorId, "USER_DEACTIVATED", "User", id,
                    Map.of("status", oldStatus), Map.of("status", request.status()), "success");
        } else {
            // AU-2: audit all other update operations (name/role/status changes that don't trigger deactivation)
            auditService.log(actorId, "USER_UPDATED", "User", id,
                    Map.of("fullName", oldName, "status", oldStatus),
                    Map.of("fullName", request.fullName(), "status", request.status()),
                    "success");
        }

        return mapToResponse(user);
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        // AC-5: no single user may delete their own account
        if (id.equals(actorId)) {
            throw new ForbiddenException("AC-5: users may not delete their own account");
        }
        var user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        // AC-2: immediately revoke all active sessions before soft-deleting
        revokeAllSessions(id);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
        permissionCacheService.evict(id);
        auditService.log(actorId, "USER_DELETED", "User", id,
                Map.of("email", user.getEmail()), null, "success");
    }

    // AC-7: admin manually clears a temporary lockout before the 15-minute window expires
    @Transactional
    public void unlock(UUID userId, UUID actorId) {
        // AC-5: an admin may not unlock their own account (would bypass lockout enforcement)
        if (userId.equals(actorId)) {
            throw new ForbiddenException("AC-5: users may not unlock their own account");
        }
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        var credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Credential not found"));

        credential.setLockedUntil(null);
        credential.setFailedAttempts(0);
        credentialRepository.save(credential);

        securityEventRecorder.recordAccountUnlocked();
        auditService.log(actorId, "USER_ACCOUNT_UNLOCKED", "User", userId,
                null, Map.of("unlockedBy", actorId.toString()), "success");
    }

    @Transactional(readOnly = true)
    public LockStatusResponse getLockStatus(UUID userId) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        var credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Credential not found"));

        boolean locked = credential.getLockedUntil() != null
                && credential.getLockedUntil().isAfter(Instant.now());
        return new LockStatusResponse(locked, credential.getLockedUntil(), credential.getFailedAttempts());
    }

    // IA-5(1)(c): admin sets a temporary password, forcing the user to change it on next login
    @Transactional
    public void adminResetPassword(UUID id, AdminResetPasswordRequest request, UUID actorId) {
        // AC-5: an admin may not reset their own password through this privileged endpoint
        if (id.equals(actorId)) {
            throw new ForbiddenException("AC-5: users may not reset their own password via admin endpoint");
        }
        var user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        var credential = credentialRepository.findByUserId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Credential not found"));

        passwordPolicyService.validate(request.temporaryPassword(), user.getEmail(), user.getFullName());
        passwordHistoryService.checkNotReused(id, request.temporaryPassword());

        var encoded = passwordEncoder.encode(request.temporaryPassword());
        credential.setPasswordHash(encoded);
        credential.setPasswordChangedAt(Instant.now());
        credential.setMustChangePassword(true);
        credentialRepository.save(credential);
        passwordHistoryService.record(id, encoded);
        revokeAllSessions(id);
        permissionCacheService.evict(id);

        auditService.log(actorId, "USER_PASSWORD_ADMIN_RESET", "User", id,
                null, Map.of("resetBy", actorId.toString()), "success");
    }

    private void revokeAllSessions(UUID userId) {
        var jtis = sessionActivityService.getSessionJtis(userId);
        long maxTtl = jwtConfig.getAccessExpirySeconds();
        jtis.forEach(jti -> {
            tokenDenylistService.deny(jti, maxTtl);
            sessionActivityService.remove(jti);
        });
        sessionActivityService.clearUserSessions(userId);
        if (!jtis.isEmpty()) {
            securityEventRecorder.recordUserSessionsRevoked(jtis.size());
        }
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
