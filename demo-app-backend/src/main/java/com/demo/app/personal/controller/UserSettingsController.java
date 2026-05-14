package com.demo.app.personal.controller;

import com.demo.app.personal.dto.AppSettingsDto;
import com.demo.app.personal.service.UserSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService settingsService;

    @GetMapping
    public ResponseEntity<AppSettingsDto> get(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(settingsService.findByUserId(UUID.fromString(userId)));
    }

    @PutMapping
    public ResponseEntity<AppSettingsDto> update(@AuthenticationPrincipal String userId,
                                                 @RequestBody @Valid AppSettingsDto dto) {
        return ResponseEntity.ok(settingsService.update(UUID.fromString(userId), dto));
    }
}
