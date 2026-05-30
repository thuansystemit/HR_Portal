package com.demo.app.iam.service;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.config.JwtConfig;
import com.demo.app.iam.dto.CreateUserRequest;
import com.demo.app.iam.dto.UpdateUserRequest;
import com.demo.app.iam.entity.Role;
import com.demo.app.iam.entity.User;
import com.demo.app.iam.entity.UserRole;
import com.demo.app.iam.repository.*;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ForbiddenException;
import com.demo.app.platform.metrics.SecurityEventRecorder;
import com.demo.app.iam.service.PasswordPolicyService;
import com.demo.app.platform.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock CredentialRepository credentialRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PermissionCacheService permissionCacheService;
    @Mock SessionActivityService sessionActivityService;
    @Mock TokenDenylistService tokenDenylistService;
    @Mock JwtConfig jwtConfig;
    @Mock AuditService auditService;
    @Mock SecurityEventRecorder securityEventRecorder;
    @Mock PasswordPolicyService passwordPolicyService;
    @Mock PasswordHistoryService passwordHistoryService;

    @InjectMocks
    UserService userService;

    private final UUID USER_ID  = UUID.randomUUID();
    private final UUID ROLE_ID  = UUID.randomUUID();
    private final UUID ACTOR_ID = UUID.randomUUID();

    @Test
    void findById_returnsResponse_whenFound() {
        var user = User.builder().id(USER_ID).fullName("Test").email("t@t.com")
                .status("active").createdAt(Instant.now()).build();
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of());

        var result = userService.findById(USER_ID);

        assertThat(result.id()).isEqualTo(USER_ID);
        assertThat(result.fullName()).isEqualTo("Test");
    }

    @Test
    void findById_throws_whenNotFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_succeeds_whenEmailAvailable() {
        var req = new CreateUserRequest("Test User", "t@t.com", ROLE_ID, "SecurePass!99x", "active");
        var user = User.builder().id(USER_ID).fullName("Test User").email("t@t.com")
                .status("active").createdAt(Instant.now()).build();
        var role = Role.builder().id(ROLE_ID).name("Viewer").build();

        when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("t@t.com")).thenReturn(false);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenReturn(user);
        when(credentialRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRoleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(
                List.of(UserRole.builder().userId(USER_ID).roleId(ROLE_ID).build()));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("SecurePass!99x")).thenReturn("$encoded");

        var result = userService.create(req, ACTOR_ID);

        assertThat(result.email()).isEqualTo("t@t.com");
        // IA-5(1)(f): provisioned credential must require password change on first login
        var credCaptor = ArgumentCaptor.forClass(com.demo.app.iam.entity.Credential.class);
        verify(credentialRepository).save(credCaptor.capture());
        assertThat(credCaptor.getValue().isMustChangePassword()).isTrue();
        verify(userRoleRepository).save(any());
        // AU-2: user creation must be audited
        verify(auditService).log(eq(ACTOR_ID), eq("USER_CREATED"), eq("User"),
                eq(USER_ID), isNull(), any(), eq("success"));
    }

    @Test
    void create_throws_whenEmailExists() {
        var req = new CreateUserRequest("Test", "t@t.com", ROLE_ID, "SecurePass!99x", null);
        when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("t@t.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(req, ACTOR_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already in use");

        verify(userRepository, never()).save(any());
    }

    // --- AC-5: Separation of Duties — self-operation guards ---

    @Test
    void update_throws_whenActorIsTarget() {
        assertThatThrownBy(() -> userService.update(USER_ID,
                new UpdateUserRequest("Name", ROLE_ID, "active"), USER_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("AC-5");
        verify(userRepository, never()).save(any());
    }

    @Test
    void delete_throws_whenActorIsTarget() {
        assertThatThrownBy(() -> userService.delete(USER_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("AC-5");
        verify(userRepository, never()).save(any());
    }

    @Test
    void unlock_throws_whenActorIsTarget() {
        assertThatThrownBy(() -> userService.unlock(USER_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("AC-5");
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void delete_softDeletes_user() {
        var user = User.builder().id(USER_ID).email("u@t.com").build();
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);
        when(sessionActivityService.getSessionJtis(USER_ID)).thenReturn(Set.of());
        when(jwtConfig.getAccessExpirySeconds()).thenReturn(28800L);

        userService.delete(USER_ID, ACTOR_ID);

        assertThat(user.getDeletedAt()).isNotNull();
        verify(permissionCacheService).evict(USER_ID);
        verify(sessionActivityService).clearUserSessions(USER_ID);
    }

    @Test
    void delete_throws_whenNotFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(USER_ID, ACTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_revokesActiveSessions_whenSessionsExist() {
        var jti1 = "jti-aaa";
        var jti2 = "jti-bbb";
        var user = User.builder().id(USER_ID).email("u@t.com").build();

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);
        when(sessionActivityService.getSessionJtis(USER_ID)).thenReturn(Set.of(jti1, jti2));
        when(jwtConfig.getAccessExpirySeconds()).thenReturn(28800L);

        userService.delete(USER_ID, ACTOR_ID);

        verify(tokenDenylistService).deny(jti1, 28800L);
        verify(tokenDenylistService).deny(jti2, 28800L);
        verify(sessionActivityService).remove(jti1);
        verify(sessionActivityService).remove(jti2);
        verify(sessionActivityService).clearUserSessions(USER_ID);
    }

    @Test
    void delete_succeedsWithNoActiveSessions() {
        var user = User.builder().id(USER_ID).email("u@t.com").build();

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);
        when(sessionActivityService.getSessionJtis(USER_ID)).thenReturn(Set.of());
        when(jwtConfig.getAccessExpirySeconds()).thenReturn(28800L);

        userService.delete(USER_ID, ACTOR_ID);

        verify(tokenDenylistService, never()).deny(anyString(), anyLong());
        verify(sessionActivityService, never()).remove(any());
        verify(sessionActivityService).clearUserSessions(USER_ID);
    }

    @Test
    void list_returnsPaged() {
        var user = User.builder().id(USER_ID).fullName("Test").email("t@t.com")
                .status("active").createdAt(Instant.now()).build();
        when(userRepository.findAllActive(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of());

        var result = userService.list(0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    // Task 3.2 -- additional coverage for update

    @Test
    void update_succeeds_whenRoleChanged() {
        var user = User.builder().id(USER_ID).fullName("Old Name").email("t@t.com")
                .status("active").createdAt(Instant.now()).build();
        var newRoleId = UUID.randomUUID();
        var role = Role.builder().id(newRoleId).name("Manager").build();
        var req = new UpdateUserRequest("New Name", newRoleId, "active");

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findById(newRoleId)).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenReturn(user);
        when(userRoleRepository.findByUserIdAndRoleId(USER_ID, newRoleId)).thenReturn(Optional.empty());
        when(userRoleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        // 1st call: getOldRole inside if block; 2nd call: mapToResponse
        when(userRoleRepository.findByUserId(USER_ID))
                .thenReturn(List.of(UserRole.builder().userId(USER_ID).roleId(ROLE_ID).build()))
                .thenReturn(List.of(UserRole.builder().userId(USER_ID).roleId(newRoleId).build()));
        when(sessionActivityService.getSessionJtis(USER_ID)).thenReturn(Set.of());
        when(jwtConfig.getAccessExpirySeconds()).thenReturn(28800L);

        var result = userService.update(USER_ID, req, ACTOR_ID);

        assertThat(result.fullName()).isEqualTo("New Name");
        verify(userRoleRepository).deleteByUserId(USER_ID);
        verify(permissionCacheService).evict(USER_ID);
    }

    @Test
    void update_revokesSessionsAndEmitsRoleChangedAudit_whenRoleChanges() {
        // IA-11: privilege change must invalidate all existing sessions immediately
        var jti = "jti-abc";
        var newRoleId = UUID.randomUUID();
        var user = User.builder().id(USER_ID).fullName("Test").email("t@t.com")
                .status("active").createdAt(Instant.now()).build();
        var req = new UpdateUserRequest("Test", newRoleId, "active");

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findById(newRoleId)).thenReturn(Optional.of(
                Role.builder().id(newRoleId).name("Admin").build()));
        when(userRepository.save(any())).thenReturn(user);
        when(userRoleRepository.findByUserIdAndRoleId(USER_ID, newRoleId)).thenReturn(Optional.empty());
        when(userRoleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRoleRepository.findByUserId(USER_ID))
                .thenReturn(List.of(UserRole.builder().userId(USER_ID).roleId(ROLE_ID).build()))
                .thenReturn(List.of(UserRole.builder().userId(USER_ID).roleId(newRoleId).build()));
        when(sessionActivityService.getSessionJtis(USER_ID)).thenReturn(Set.of(jti));
        when(jwtConfig.getAccessExpirySeconds()).thenReturn(28800L);

        userService.update(USER_ID, req, ACTOR_ID);

        verify(tokenDenylistService).deny(jti, 28800L);
        verify(sessionActivityService).clearUserSessions(USER_ID);
        verify(auditService).log(eq(ACTOR_ID), eq("USER_ROLE_CHANGED"), eq("User"),
                eq(USER_ID), eq(Map.of("roleId", ROLE_ID.toString())),
                eq(Map.of("roleId", newRoleId.toString())), eq("success"));
    }

    @Test
    void update_passesNullOldRoleId_whenUserHasNoCurrentRoleAssignment() {
        // covers the isEmpty==true and oldRoleId==null ternary branches in UserService.update()
        var user = User.builder().id(USER_ID).fullName("Test").email("t@t.com")
                .status("active").createdAt(Instant.now()).build();
        var req = new UpdateUserRequest("Test", ROLE_ID, "active");

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(
                Role.builder().id(ROLE_ID).name("Viewer").build()));
        when(userRepository.save(any())).thenReturn(user);
        when(userRoleRepository.findByUserIdAndRoleId(USER_ID, ROLE_ID)).thenReturn(Optional.empty());
        when(userRoleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRoleRepository.findByUserId(USER_ID))
                .thenReturn(List.of())  // 1st call: no prior role → oldRoleId = null
                .thenReturn(List.of(UserRole.builder().userId(USER_ID).roleId(ROLE_ID).build()));
        when(sessionActivityService.getSessionJtis(USER_ID)).thenReturn(Set.of());
        when(jwtConfig.getAccessExpirySeconds()).thenReturn(28800L);

        userService.update(USER_ID, req, ACTOR_ID);

        // audit before should be null when there was no prior role
        verify(auditService).log(eq(ACTOR_ID), eq("USER_ROLE_CHANGED"), eq("User"),
                eq(USER_ID), isNull(), eq(Map.of("roleId", ROLE_ID.toString())), eq("success"));
    }

    @Test
    void update_doesNotRevokeSessionsOrEmitRoleChanged_whenRoleUnchanged() {
        // IA-11: no revocation when only name/status changes; no spurious USER_ROLE_CHANGED event
        var user = User.builder().id(USER_ID).fullName("Test").email("t@t.com")
                .status("active").createdAt(Instant.now()).build();
        var req = new UpdateUserRequest("New Name", ROLE_ID, "active");
        var existingAssignment = UserRole.builder().userId(USER_ID).roleId(ROLE_ID).build();

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(
                Role.builder().id(ROLE_ID).name("Viewer").build()));
        when(userRepository.save(any())).thenReturn(user);
        when(userRoleRepository.findByUserIdAndRoleId(USER_ID, ROLE_ID))
                .thenReturn(Optional.of(existingAssignment));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(existingAssignment));

        userService.update(USER_ID, req, ACTOR_ID);

        verify(tokenDenylistService, never()).deny(anyString(), anyLong());
        verify(sessionActivityService, never()).clearUserSessions(any());
        verify(auditService, never()).log(any(), eq("USER_ROLE_CHANGED"), any(), any(), any(), any(), any());
    }

    @Test
    void update_succeeds_whenRoleSame() {
        var user = User.builder().id(USER_ID).fullName("Test").email("t@t.com")
                .status("active").createdAt(Instant.now()).build();
        var role = Role.builder().id(ROLE_ID).name("Viewer").build();
        var req = new UpdateUserRequest("Test Updated", ROLE_ID, "active");
        var existingAssignment = UserRole.builder().userId(USER_ID).roleId(ROLE_ID).build();

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenReturn(user);
        when(userRoleRepository.findByUserIdAndRoleId(USER_ID, ROLE_ID)).thenReturn(Optional.of(existingAssignment));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(existingAssignment));

        userService.update(USER_ID, req, ACTOR_ID);

        verify(userRoleRepository, never()).deleteByUserId(any());
        verify(permissionCacheService, never()).evict(any());
    }

    @Test
    void update_throws_whenNotFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(USER_ID, new UpdateUserRequest("Name", ROLE_ID, "active"), ACTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_revokesSessions_whenStatusChangesToInactive() {
        var jti = "jti-xyz";
        var user = User.builder().id(USER_ID).fullName("Test").email("t@t.com")
                .status("active").createdAt(Instant.now()).build();
        var role = Role.builder().id(ROLE_ID).name("Viewer").build();
        var req = new UpdateUserRequest("Test", ROLE_ID, "inactive");
        var existingAssignment = UserRole.builder().userId(USER_ID).roleId(ROLE_ID).build();

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenReturn(user);
        when(userRoleRepository.findByUserIdAndRoleId(USER_ID, ROLE_ID)).thenReturn(Optional.of(existingAssignment));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(existingAssignment));
        when(sessionActivityService.getSessionJtis(USER_ID)).thenReturn(Set.of(jti));
        when(jwtConfig.getAccessExpirySeconds()).thenReturn(28800L);

        userService.update(USER_ID, req, ACTOR_ID);

        verify(tokenDenylistService).deny(jti, 28800L);
        verify(sessionActivityService).remove(jti);
        verify(sessionActivityService).clearUserSessions(USER_ID);
    }

    @Test
    void update_doesNotRevokeSessions_whenStatusStaysActive() {
        var user = User.builder().id(USER_ID).fullName("Test").email("t@t.com")
                .status("active").createdAt(Instant.now()).build();
        var role = Role.builder().id(ROLE_ID).name("Viewer").build();
        var req = new UpdateUserRequest("Test Updated", ROLE_ID, "active");
        var existingAssignment = UserRole.builder().userId(USER_ID).roleId(ROLE_ID).build();

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenReturn(user);
        when(userRoleRepository.findByUserIdAndRoleId(USER_ID, ROLE_ID)).thenReturn(Optional.of(existingAssignment));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(existingAssignment));

        userService.update(USER_ID, req, ACTOR_ID);

        verify(tokenDenylistService, never()).deny(anyString(), anyLong());
        verify(sessionActivityService, never()).clearUserSessions(any());
    }

    @Test
    void update_emitsUserUpdated_whenNotDeactivating() {
        // AU-2: non-deactivation updates must emit USER_UPDATED, not USER_DEACTIVATED
        var user = User.builder().id(USER_ID).fullName("Old Name").email("t@t.com")
                .status("active").createdAt(Instant.now()).build();
        var role = Role.builder().id(ROLE_ID).name("Viewer").build();
        var req = new UpdateUserRequest("New Name", ROLE_ID, "active");
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenReturn(user);
        when(userRoleRepository.findByUserIdAndRoleId(USER_ID, ROLE_ID))
                .thenReturn(Optional.of(UserRole.builder().userId(USER_ID).roleId(ROLE_ID).build()));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of());

        userService.update(USER_ID, req, ACTOR_ID);

        verify(auditService).log(eq(ACTOR_ID), eq("USER_UPDATED"), eq("User"),
                eq(USER_ID), any(), any(), eq("success"));
        verify(auditService, never()).log(any(), eq("USER_DEACTIVATED"), any(), any(), any(), any(), any());
    }

    @Test
    void create_defaultsStatusToActive_whenStatusNull() {
        var req = new CreateUserRequest("Test User", "default@t.com", ROLE_ID, "SecurePass!99x", null);
        var user = User.builder().id(USER_ID).fullName("Test User").email("default@t.com")
                .status("active").createdAt(Instant.now()).build();
        var role = Role.builder().id(ROLE_ID).name("Viewer").build();

        when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("default@t.com")).thenReturn(false);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenReturn(user);
        when(credentialRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRoleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(passwordEncoder.encode("SecurePass!99x")).thenReturn("$encoded");

        var result = userService.create(req, ACTOR_ID);

        assertThat(result.status()).isEqualTo("active");
    }

    @Test
    void update_throws_whenRoleNotFound() {
        var user = User.builder().id(USER_ID).fullName("Test").email("t@t.com")
                .status("active").createdAt(Instant.now()).build();
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(USER_ID, new UpdateUserRequest("Name", ROLE_ID, "active"), ACTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listByRoleName_returnsUsers_whenRoleFound() {
        var role = Role.builder().id(ROLE_ID).name("Manager").build();
        var user = User.builder().id(USER_ID).fullName("Alice").email("alice@t.com")
                .status("active").createdAt(Instant.now()).build();

        when(roleRepository.findByName("Manager")).thenReturn(Optional.of(role));
        when(userRepository.findActiveByRoleId(ROLE_ID)).thenReturn(List.of(user));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of());

        var result = userService.listByRoleName("Manager");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Alice");
    }

    @Test
    void listByRoleName_returnsEmpty_whenRoleNotFound() {
        when(roleRepository.findByName("Unknown")).thenReturn(Optional.empty());

        var result = userService.listByRoleName("Unknown");

        assertThat(result).isEmpty();
    }

    // --- unlock ---

    @Test
    void unlock_clearsLockAndAudits_whenUserIsLocked() {
        var user = User.builder().id(USER_ID).email("u@t.com").build();
        var lockedUntil = Instant.now().plusSeconds(600);
        var cred = com.demo.app.iam.entity.Credential.builder()
                .userId(USER_ID).failedAttempts(5).lockedUntil(lockedUntil)
                .passwordHash("$h").build();

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cred));
        when(credentialRepository.save(any())).thenReturn(cred);

        userService.unlock(USER_ID, ACTOR_ID);

        assertThat(cred.getLockedUntil()).isNull();
        assertThat(cred.getFailedAttempts()).isZero();
        verify(credentialRepository).save(cred);
        verify(securityEventRecorder).recordAccountUnlocked();
        verify(auditService).log(eq(ACTOR_ID), eq("USER_ACCOUNT_UNLOCKED"), eq("User"),
                eq(USER_ID), isNull(), any(), eq("success"));
    }

    @Test
    void unlock_throws_whenUserNotFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.unlock(USER_ID, ACTOR_ID))
                .isInstanceOf(com.demo.app.platform.exception.ResourceNotFoundException.class);
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void unlock_throws_whenCredentialNotFound() {
        var user = User.builder().id(USER_ID).email("u@t.com").build();
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.unlock(USER_ID, ACTOR_ID))
                .isInstanceOf(com.demo.app.platform.exception.ResourceNotFoundException.class);
        verify(credentialRepository, never()).save(any());
    }

    // --- getLockStatus ---

    @Test
    void getLockStatus_returnsLocked_whenLockedUntilInFuture() {
        var user = User.builder().id(USER_ID).email("u@t.com").build();
        var lockedUntil = Instant.now().plusSeconds(300);
        var cred = com.demo.app.iam.entity.Credential.builder()
                .userId(USER_ID).failedAttempts(5).lockedUntil(lockedUntil)
                .passwordHash("$h").build();

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cred));

        var result = userService.getLockStatus(USER_ID);

        assertThat(result.locked()).isTrue();
        assertThat(result.lockedUntil()).isEqualTo(lockedUntil);
        assertThat(result.failedAttempts()).isEqualTo(5);
    }

    @Test
    void getLockStatus_returnsUnlocked_whenLockedUntilIsNull() {
        var user = User.builder().id(USER_ID).email("u@t.com").build();
        var cred = com.demo.app.iam.entity.Credential.builder()
                .userId(USER_ID).failedAttempts(0).passwordHash("$h").build();

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cred));

        var result = userService.getLockStatus(USER_ID);

        assertThat(result.locked()).isFalse();
        assertThat(result.lockedUntil()).isNull();
    }

    @Test
    void getLockStatus_returnsUnlocked_whenLockedUntilExpired() {
        var user = User.builder().id(USER_ID).email("u@t.com").build();
        var cred = com.demo.app.iam.entity.Credential.builder()
                .userId(USER_ID).failedAttempts(3)
                .lockedUntil(Instant.now().minusSeconds(1))
                .passwordHash("$h").build();

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cred));

        var result = userService.getLockStatus(USER_ID);

        assertThat(result.locked()).isFalse();
    }

    @Test
    void getLockStatus_throws_whenUserNotFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getLockStatus(USER_ID))
                .isInstanceOf(com.demo.app.platform.exception.ResourceNotFoundException.class);
    }

    // --- adminResetPassword ---

    @Test
    void adminResetPassword_setsMustChangePassword_andRevokesAllSessions() {
        var user = User.builder().id(USER_ID).email("u@t.com").fullName("Test User").status("active").build();
        var cred = com.demo.app.iam.entity.Credential.builder().userId(USER_ID).passwordHash("$old").build();
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cred));
        when(passwordEncoder.encode("TempPass1234!")).thenReturn("$temp");
        when(credentialRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sessionActivityService.getSessionJtis(USER_ID)).thenReturn(Set.of("jti-1"));

        userService.adminResetPassword(USER_ID, new com.demo.app.iam.dto.AdminResetPasswordRequest("TempPass1234!"), ACTOR_ID);

        assertThat(cred.isMustChangePassword()).isTrue();
        assertThat(cred.getPasswordHash()).isEqualTo("$temp");
        verify(sessionActivityService).getSessionJtis(USER_ID);
        verify(tokenDenylistService).deny(eq("jti-1"), anyLong());
    }

    @Test
    void adminResetPassword_emitsAuditEvent() {
        var user = User.builder().id(USER_ID).email("u@t.com").fullName("Test User").status("active").build();
        var cred = com.demo.app.iam.entity.Credential.builder().userId(USER_ID).passwordHash("$old").build();
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cred));
        when(passwordEncoder.encode(any())).thenReturn("$temp");
        when(credentialRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sessionActivityService.getSessionJtis(USER_ID)).thenReturn(Set.of());

        userService.adminResetPassword(USER_ID, new com.demo.app.iam.dto.AdminResetPasswordRequest("TempPass1234!"), ACTOR_ID);

        verify(auditService).log(eq(ACTOR_ID), eq("USER_PASSWORD_ADMIN_RESET"), eq("User"),
                eq(USER_ID), isNull(), argThat(m -> m.containsKey("resetBy")), eq("success"));
    }

    @Test
    void adminResetPassword_throws_whenActorIsTarget() {
        assertThatThrownBy(() -> userService.adminResetPassword(
                USER_ID, new com.demo.app.iam.dto.AdminResetPasswordRequest("TempPass1234!"), USER_ID))
                .isInstanceOf(com.demo.app.platform.exception.ForbiddenException.class)
                .hasMessageContaining("AC-5");
    }

    @Test
    void adminResetPassword_throws_whenUserNotFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.adminResetPassword(
                USER_ID, new com.demo.app.iam.dto.AdminResetPasswordRequest("TempPass1234!"), ACTOR_ID))
                .isInstanceOf(com.demo.app.platform.exception.ResourceNotFoundException.class);
    }

    @Test
    void adminResetPassword_validatesPasswordPolicy() {
        var user = User.builder().id(USER_ID).email("u@t.com").fullName("Test User").status("active").build();
        var cred = com.demo.app.iam.entity.Credential.builder().userId(USER_ID).passwordHash("$old").build();
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cred));

        userService.adminResetPassword(USER_ID, new com.demo.app.iam.dto.AdminResetPasswordRequest("TempPass1234!"), ACTOR_ID);

        verify(passwordPolicyService).validate("TempPass1234!", "u@t.com", "Test User");
    }

    @Test
    void adminResetPassword_checksPasswordHistory() {
        var user = User.builder().id(USER_ID).email("u@t.com").fullName("Test User").status("active").build();
        var cred = com.demo.app.iam.entity.Credential.builder().userId(USER_ID).passwordHash("$old").build();
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cred));
        when(passwordEncoder.encode(any())).thenReturn("$temp");
        when(credentialRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sessionActivityService.getSessionJtis(USER_ID)).thenReturn(Set.of());

        userService.adminResetPassword(USER_ID, new com.demo.app.iam.dto.AdminResetPasswordRequest("TempPass1234!"), ACTOR_ID);

        verify(passwordHistoryService).checkNotReused(USER_ID, "TempPass1234!");
        verify(passwordHistoryService).record(USER_ID, "$temp");
    }

    @Test
    void user_fullName_isAnnotatedWithPiiEncryptionConverter() throws NoSuchFieldException {
        var field = com.demo.app.iam.entity.User.class.getDeclaredField("fullName");
        var convert = field.getAnnotation(jakarta.persistence.Convert.class);
        assertThat(convert).isNotNull();
        assertThat(convert.converter()).isEqualTo(com.demo.app.platform.security.encryption.PiiEncryptionConverter.class);
    }
}
