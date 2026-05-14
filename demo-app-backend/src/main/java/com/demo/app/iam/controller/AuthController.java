package com.demo.app.iam.controller;

import com.demo.app.iam.dto.AuthResponse;
import com.demo.app.iam.dto.ChangePasswordRequest;
import com.demo.app.iam.dto.LoginRequest;
import com.demo.app.iam.dto.UserInfo;
import com.demo.app.iam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        var ip = httpRequest.getRemoteAddr();
        var ua = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.login(request, httpResponse, ip, ua));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request,
                                                HttpServletResponse response) {
        return ResponseEntity.ok(authService.refresh(request, response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfo> me(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(authService.getMe(UUID.fromString(userId)));
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal String userId,
                                               @RequestBody @Valid ChangePasswordRequest request) {
        authService.changePassword(UUID.fromString(userId), request);
        return ResponseEntity.noContent().build();
    }
}
