package com.demo.app.iam.service;

import com.demo.app.iam.dto.ChangePasswordRequest;
import com.demo.app.iam.dto.LoginRequest;
import com.demo.app.iam.entity.Credential;
import com.demo.app.iam.entity.RefreshToken;
import com.demo.app.iam.entity.Role;
import com.demo.app.iam.entity.User;
import com.demo.app.iam.repository.*;
import com.demo.app.platform.exception.ForbiddenException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import com.demo.app.platform.exception.WrongPasswordException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock CredentialRepository credentialRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock PermissionCacheService permissionCacheService;
    @Mock HttpServletResponse httpResponse;
    @Mock HttpServletRequest httpRequest;

    @InjectMocks
    AuthService authService;

    private final UUID USER_ID = UUID.randomUUID();

    @Test
    void login_success_returnsAuthResponse() {
        var user = User.builder().id(USER_ID).email("a@b.com").status("active").fullName("Test").build();
        var cred = Credential.builder().userId(USER_ID).passwordHash("$hash").failedAttempts(0).build();
        var permSet = new PermissionCacheService.PermissionSet(UUID.randomUUID(), Set.of("usersView"));

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("a@b.com"))
                .thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cred));
        when(passwordEncoder.matches("pass123", "$hash")).thenReturn(true);
        when(credentialRepository.save(any())).thenReturn(cred);
        when(permissionCacheService.loadPermissions(USER_ID)).thenReturn(permSet);
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("jwt.token.here");
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findById(any())).thenReturn(Optional.empty());

        var result = authService.login(new LoginRequest("a@b.com", "pass123"),
                httpResponse, "127.0.0.1", "Mozilla");

        assertThat(result.user().email()).isEqualTo("a@b.com");
        assertThat(result.user().permissions()).contains("usersView");
    }

    @Test
    void login_throws_whenUserNotFound() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("x@y.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("x@y.com", "pass"), httpResponse, "ip", "ua"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_throws_whenAccountInactive() {
        var user = User.builder().id(USER_ID).email("a@b.com").status("inactive").build();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("a@b.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("a@b.com", "pass"), httpResponse, "ip", "ua"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void login_throws_whenAccountLocked() {
        var user = User.builder().id(USER_ID).email("a@b.com").status("active").build();
        var cred = Credential.builder().userId(USER_ID).passwordHash("h")
                .lockedUntil(Instant.now().plusSeconds(300)).build();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("a@b.com"))
                .thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cred));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("a@b.com", "pass"), httpResponse, "ip", "ua"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void login_throws_whenPasswordWrong() {
        var user = User.builder().id(USER_ID).email("a@b.com").status("active").build();
        var cred = Credential.builder().userId(USER_ID).passwordHash("hash")
                .failedAttempts(0).build();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("a@b.com"))
                .thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cred));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        when(credentialRepository.save(any())).thenReturn(cred);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("a@b.com", "wrong"), httpResponse, "ip", "ua"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Invalid credentials");

        verify(credentialRepository).save(argThat(c -> c.getFailedAttempts() == 1));
    }

    @Test
    void sha256_returnsDeterministicHex() {
        var h1 = authService.sha256("test");
        var h2 = authService.sha256("test");
        assertThat(h1).isEqualTo(h2).hasSize(64);
    }

    @Test
    void logout_revokesToken_whenPresent() {
        var raw = UUID.randomUUID().toString();
        var hash = authService.sha256(raw);
        var stored = RefreshToken.builder().tokenHash(hash).userId(USER_ID).build();

        when(httpRequest.getCookies()).thenReturn(new jakarta.servlet.http.Cookie[]{
                new jakarta.servlet.http.Cookie("refresh-token", raw)
        });
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(hash))
                .thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any())).thenReturn(stored);

        authService.logout(httpRequest, httpResponse);

        assertThat(stored.getRevokedAt()).isNotNull();
    }

    // Task 3.1 -- additional coverage for refresh, getMe, changePassword, handleFailedLogin

    @Test
    void refresh_succeeds_withValidToken() {
        var raw = UUID.randomUUID().toString();
        var hash = authService.sha256(raw);
        var user = User.builder().id(USER_ID).email("a@b.com").fullName("Test").status("active").build();
        var stored = RefreshToken.builder()
                .tokenHash(hash)
                .userId(USER_ID)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        var permSet = new PermissionCacheService.PermissionSet(UUID.randomUUID(), Set.of("users.view"));

        when(httpRequest.getCookies()).thenReturn(new jakarta.servlet.http.Cookie[]{
                new jakarta.servlet.http.Cookie("refresh-token", raw)
        });
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(hash))
                .thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(permissionCacheService.loadPermissions(USER_ID)).thenReturn(permSet);
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("new.access.token");
        when(roleRepository.findById(any())).thenReturn(Optional.empty());

        var result = authService.refresh(httpRequest, httpResponse);

        assertThat(result).isNotNull();
        assertThat(stored.getRevokedAt()).isNotNull();
        verify(jwtService).generateAccessToken(any(), any(), any());
    }

    @Test
    void refresh_throws_whenNoRefreshCookie() {
        when(httpRequest.getCookies()).thenReturn(null);

        assertThatThrownBy(() -> authService.refresh(httpRequest, httpResponse))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("No refresh token");
    }

    @Test
    void refresh_throws_whenTokenExpired() {
        var raw = UUID.randomUUID().toString();
        var hash = authService.sha256(raw);
        var stored = RefreshToken.builder()
                .tokenHash(hash)
                .userId(USER_ID)
                .expiresAt(Instant.now().minusSeconds(100))
                .build();

        when(httpRequest.getCookies()).thenReturn(new jakarta.servlet.http.Cookie[]{
                new jakarta.servlet.http.Cookie("refresh-token", raw)
        });
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(hash))
                .thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any())).thenReturn(stored);

        assertThatThrownBy(() -> authService.refresh(httpRequest, httpResponse))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("expired");

        assertThat(stored.getRevokedAt()).isNotNull();
    }

    @Test
    void refresh_throws_whenTokenNotFound() {
        var raw = UUID.randomUUID().toString();
        var hash = authService.sha256(raw);

        when(httpRequest.getCookies()).thenReturn(new jakarta.servlet.http.Cookie[]{
                new jakarta.servlet.http.Cookie("refresh-token", raw)
        });
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(hash))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(httpRequest, httpResponse))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void getMe_returnsUserInfo() {
        var user = User.builder().id(USER_ID).email("a@b.com").fullName("Test").status("active").build();
        var roleId = UUID.randomUUID();
        var permSet = new PermissionCacheService.PermissionSet(roleId, Set.of("users.view"));
        var role = Role.builder().id(roleId).name("Admin").build();

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(permissionCacheService.loadPermissions(USER_ID)).thenReturn(permSet);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

        var result = authService.getMe(USER_ID);

        assertThat(result.id()).isEqualTo(USER_ID);
        assertThat(result.email()).isEqualTo("a@b.com");
        assertThat(result.roleName()).isEqualTo("Admin");
        assertThat(result.permissions()).contains("users.view");
    }

    @Test
    void getMe_throws_whenNotFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getMe(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void changePassword_succeeds() {
        var cred = Credential.builder().userId(USER_ID).passwordHash("$old").build();
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cred));
        when(passwordEncoder.matches("oldPass", "$old")).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("$new");
        when(credentialRepository.save(any())).thenReturn(cred);

        authService.changePassword(USER_ID, new ChangePasswordRequest("oldPass", "newPass", "newPass"));

        assertThat(cred.getPasswordHash()).isEqualTo("$new");
        verify(permissionCacheService).evict(USER_ID);
    }

    @Test
    void changePassword_throws_whenCurrentPasswordWrong() {
        var cred = Credential.builder().userId(USER_ID).passwordHash("$old").build();
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cred));
        when(passwordEncoder.matches("wrongPass", "$old")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(USER_ID,
                new ChangePasswordRequest("wrongPass", "newPass", "newPass")))
                .isInstanceOf(WrongPasswordException.class);

        verify(credentialRepository, never()).save(any());
    }

    @Test
    void logout_noOp_whenNoCookies() {
        when(httpRequest.getCookies()).thenReturn(null);

        // Should complete without exception
        authService.logout(httpRequest, httpResponse);

        verify(refreshTokenRepository, never()).findByTokenHashAndRevokedAtIsNull(any());
    }

    @Test
    void getMe_returnsNullRoleName_whenNoRole() {
        var user = User.builder().id(USER_ID).email("a@b.com").fullName("Test").status("active").build();
        // PermissionSet with null roleId means no role is attached
        var permSet = new PermissionCacheService.PermissionSet(null, Set.of());

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));
        when(permissionCacheService.loadPermissions(USER_ID)).thenReturn(permSet);

        var result = authService.getMe(USER_ID);

        assertThat(result.id()).isEqualTo(USER_ID);
        assertThat(result.roleName()).isNull();
        assertThat(result.roleId()).isNull();
    }

    @Test
    void handleFailedLogin_locksAfterFiveAttempts() {
        var cred = Credential.builder().userId(USER_ID).passwordHash("hash")
                .failedAttempts(4).build();
        var user = User.builder().id(USER_ID).email("a@b.com").status("active").build();

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("a@b.com"))
                .thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cred));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        when(credentialRepository.save(any())).thenReturn(cred);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("a@b.com", "wrong"), httpResponse, "ip", "ua"))
                .isInstanceOf(ForbiddenException.class);

        // After 5th failure, lockedUntil should be set
        verify(credentialRepository).save(argThat(c ->
                c.getFailedAttempts() == 5 && c.getLockedUntil() != null));
    }
}
