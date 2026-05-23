package com.demo.app.personal.controller;

import com.demo.app.iam.dto.ChangePasswordRequest;
import com.demo.app.iam.dto.UserInfo;
import com.demo.app.iam.service.AuthService;
import com.demo.app.platform.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private ProfileController profileController;

    private final UUID USER_ID = UUID.randomUUID();

    @Test
    void me_returnsOk() {
        var userId = USER_ID.toString();
        var userInfo = new UserInfo(USER_ID, "Test User", "test@example.com", null, null, Set.of());

        when(authService.getMe(USER_ID)).thenReturn(userInfo);

        var result = profileController.me(userId);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(userInfo);
    }

    @Test
    void changePassword_passwordsMatch_returns204() {
        var userId = USER_ID.toString();
        var request = new ChangePasswordRequest("oldPass", "newPass", "newPass");

        var result = profileController.changePassword(userId, request);

        verify(authService).changePassword(USER_ID, request);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void changePassword_passwordsMismatch_throwsBusinessRuleException() {
        var userId = USER_ID.toString();
        var request = new ChangePasswordRequest("oldPass", "newPass", "differentPass");

        assertThatThrownBy(() -> profileController.changePassword(userId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("do not match");
    }
}
