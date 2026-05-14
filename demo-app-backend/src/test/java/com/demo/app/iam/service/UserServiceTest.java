package com.demo.app.iam.service;

import com.demo.app.iam.dto.CreateUserRequest;
import com.demo.app.iam.dto.UpdateUserRequest;
import com.demo.app.iam.entity.Role;
import com.demo.app.iam.entity.User;
import com.demo.app.iam.entity.UserRole;
import com.demo.app.iam.repository.*;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock CredentialRepository credentialRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PermissionCacheService permissionCacheService;

    @InjectMocks
    UserService userService;

    private final UUID USER_ID = UUID.randomUUID();
    private final UUID ROLE_ID = UUID.randomUUID();

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
        var req = new CreateUserRequest("Test User", "t@t.com", ROLE_ID, "pass123", "active");
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
        when(passwordEncoder.encode("pass123")).thenReturn("$encoded");

        var result = userService.create(req);

        assertThat(result.email()).isEqualTo("t@t.com");
        verify(credentialRepository).save(any());
        verify(userRoleRepository).save(any());
    }

    @Test
    void create_throws_whenEmailExists() {
        var req = new CreateUserRequest("Test", "t@t.com", ROLE_ID, "pass", null);
        when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("t@t.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already in use");

        verify(userRepository, never()).save(any());
    }

    @Test
    void delete_softDeletes_user() {
        var user = User.builder().id(USER_ID).build();
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        userService.delete(USER_ID);

        assertThat(user.getDeletedAt()).isNotNull();
        verify(permissionCacheService).evict(USER_ID);
    }

    @Test
    void delete_throws_whenNotFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
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
}
