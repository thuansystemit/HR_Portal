package com.demo.app.iam.controller;

import com.demo.app.iam.dto.AuthResponse;
import com.demo.app.iam.dto.UserInfo;
import com.demo.app.iam.service.AuthService;
import com.demo.app.iam.service.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaControllerTest {

    @Mock MfaService mfaService;
    @Mock AuthService authService;
    @Mock HttpServletRequest httpRequest;
    @Mock HttpServletResponse httpResponse;

    @InjectMocks
    MfaController controller;

    private final UUID USER_ID = UUID.randomUUID();

    // --- setup ---

    @Test
    void setup_delegatesToMfaService_returnsOk() {
        var setupResponse = new MfaService.MfaSetupResponse("secret", "data:image/png;base64,xxx");
        when(mfaService.initSetup(USER_ID, "user@example.com")).thenReturn(setupResponse);

        var result = controller.setup(USER_ID.toString(), "user@example.com");

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(setupResponse);
    }

    @Test
    void setupVerify_returnsBackupCodes() {
        var codes = List.of("CODE1", "CODE2");
        when(mfaService.confirmSetup(eq(USER_ID), eq("123456"))).thenReturn(codes);

        var result = controller.setupVerify(USER_ID.toString(),
                new MfaController.SetupVerifyRequest("123456"));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).containsKey("backupCodes");
        assertThat(result.getBody().get("backupCodes")).isEqualTo(codes);
    }

    // --- enrollInit ---

    @Test
    void enrollInit_delegatesToMfaService_returnsSetupResponse() {
        var setupResponse = new MfaService.MfaSetupResponse("secret", "qr-uri");
        when(mfaService.initSetupByToken("enroll-token")).thenReturn(setupResponse);

        var result = controller.enrollInit(new MfaController.EnrollInitRequest("enroll-token"));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(setupResponse);
    }

    // --- enrollConfirm ---

    @Test
    void enrollConfirm_completesEnrollment_andIssuesTokens() {
        var enrollResult = new MfaService.EnrollConfirmResult(USER_ID, List.of("BC1", "BC2"));
        when(mfaService.confirmSetupByToken("enroll-token", "123456")).thenReturn(enrollResult);

        var userInfo = new UserInfo(USER_ID, "Test", "t@t.com", null, null, Set.of(), null);
        when(authService.issueTokensForUser(USER_ID, httpRequest, httpResponse))
                .thenReturn(new AuthResponse(userInfo));

        var result = controller.enrollConfirm(
                new MfaController.EnrollConfirmRequest("enroll-token", "123456"),
                httpRequest, httpResponse);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().backupCodes()).isEqualTo(List.of("BC1", "BC2"));
        assertThat(result.getBody().user()).isEqualTo(userInfo);
    }

    // --- verify ---

    @Test
    void verify_verifiesChallengeAndIssuesTokens() {
        when(mfaService.verifyChallenge("challenge-token", "654321")).thenReturn(USER_ID);

        var userInfo = new UserInfo(USER_ID, "Test", "t@t.com", null, null, Set.of(), null);
        when(authService.issueTokensForUser(USER_ID, httpRequest, httpResponse))
                .thenReturn(new AuthResponse(userInfo));

        var result = controller.verify(
                new MfaController.MfaVerifyRequest("challenge-token", "654321"),
                httpRequest, httpResponse);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().user()).isEqualTo(userInfo);
    }

    // --- disable ---

    @Test
    void disable_callsMfaServiceAndReturnsNoContent() {
        var result = controller.disable(USER_ID.toString(),
                new MfaController.DisableMfaRequest("111111"));

        verify(mfaService).disable(USER_ID, "111111");
        assertThat(result.getStatusCode().value()).isEqualTo(204);
    }
}
