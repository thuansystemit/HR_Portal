package com.demo.app.iam.controller;

import com.demo.app.iam.dto.AuthResponse;
import com.demo.app.iam.dto.ChangePasswordRequest;
import com.demo.app.iam.dto.LoginRequest;
import com.demo.app.iam.dto.UserInfo;
import com.demo.app.iam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private HttpServletRequest req;

    @Mock
    private HttpServletResponse resp;

    @InjectMocks
    private AuthController authController;

    private final UUID USER_ID = UUID.randomUUID();

    @Test
    void login_delegatesToService_returnsOk() {
        var request = new LoginRequest("a@b.com", "pass");
        var userInfo = new UserInfo(USER_ID, "Test User", "a@b.com", null, null, Set.of());
        var authResponse = new AuthResponse(userInfo);

        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        when(req.getHeader("User-Agent")).thenReturn("Mozilla");
        when(authService.login(eq(request), eq(resp), eq("127.0.0.1"), eq("Mozilla")))
                .thenReturn(authResponse);

        var result = authController.login(request, req, resp);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(authResponse);
    }

    @Test
    void refresh_delegatesToService_returnsOk() {
        var userInfo = new UserInfo(USER_ID, "Test User", "a@b.com", null, null, Set.of());
        var authResponse = new AuthResponse(userInfo);

        when(authService.refresh(req, resp)).thenReturn(authResponse);

        var result = authController.refresh(req, resp);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(authResponse);
    }

    @Test
    void logout_delegatesToService_returns204() {
        var result = authController.logout(req, resp);

        verify(authService).logout(req, resp);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void me_delegatesToService_returnsOk() {
        var userId = USER_ID.toString();
        var userInfo = new UserInfo(USER_ID, "Test User", "a@b.com", null, null, Set.of("perm.view"));

        when(authService.getMe(USER_ID)).thenReturn(userInfo);

        var result = authController.me(userId);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(userInfo);
    }

    @Test
    void changePassword_delegatesToService_returns204() {
        var userId = USER_ID.toString();
        var request = new ChangePasswordRequest("oldPass", "newPass", "newPass");

        var result = authController.changePassword(userId, request);

        verify(authService).changePassword(eq(USER_ID), eq(request));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
