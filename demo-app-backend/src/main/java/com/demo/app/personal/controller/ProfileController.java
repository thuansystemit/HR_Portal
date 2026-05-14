package com.demo.app.personal.controller;

import com.demo.app.iam.dto.ChangePasswordRequest;
import com.demo.app.iam.dto.UserInfo;
import com.demo.app.iam.service.AuthService;
import com.demo.app.platform.exception.BusinessRuleException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<UserInfo> me(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(authService.getMe(UUID.fromString(userId)));
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal String userId,
                                               @RequestBody @Valid ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessRuleException("New password and confirmation do not match");
        }
        authService.changePassword(UUID.fromString(userId), request);
        return ResponseEntity.noContent().build();
    }
}
