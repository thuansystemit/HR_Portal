package com.demo.app.iam.controller;

import com.demo.app.iam.dto.AdminResetPasswordRequest;
import com.demo.app.iam.dto.CreateUserRequest;
import com.demo.app.iam.dto.LockStatusResponse;
import com.demo.app.iam.dto.PagedResponse;
import com.demo.app.iam.dto.UpdateUserRequest;
import com.demo.app.iam.dto.UserResponse;

import java.util.List;
import com.demo.app.iam.service.UserService;
import com.demo.app.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final IdempotencyService idempotencyService;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String roleName) {
        if (roleName != null && !roleName.isBlank()) {
            List<UserResponse> users = userService.listByRoleName(roleName);
            return ResponseEntity.ok(users);
        }
        return ResponseEntity.ok(userService.list(page, size));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @RequestBody @Valid CreateUserRequest request,
            @AuthenticationPrincipal String userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey != null && userId != null) {
            var cached = idempotencyService.findCached(
                    UUID.fromString(userId), "user", idempotencyKey, UserResponse.class);
            if (cached.isPresent()) {
                return ResponseEntity.ok()
                        .header("X-Idempotent-Replayed", "true")
                        .body(cached.get());
            }
        }
        var created = userService.create(request, userId != null ? UUID.fromString(userId) : null);
        if (idempotencyKey != null && userId != null) {
            idempotencyService.store(UUID.fromString(userId), "user", idempotencyKey, created);
        }
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable UUID id,
                                               @RequestBody @Valid UpdateUserRequest request,
                                               @AuthenticationPrincipal String actorId) {
        return ResponseEntity.ok(userService.update(id, request, UUID.fromString(actorId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal String actorId) {
        userService.delete(id, UUID.fromString(actorId));
        return ResponseEntity.noContent().build();
    }

    // IA-5(1)(c): admin sets a temporary password — user must change it on next login
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('PERM_usersEdit')")
    public ResponseEntity<Void> adminResetPassword(@PathVariable UUID id,
                                                   @RequestBody @Valid AdminResetPasswordRequest request,
                                                   @AuthenticationPrincipal String actorId) {
        userService.adminResetPassword(id, request, UUID.fromString(actorId));
        return ResponseEntity.noContent().build();
    }

    // AC-7: admin endpoint to manually clear a temporary account lockout
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('PERM_usersEdit')")
    public ResponseEntity<Void> unlock(@PathVariable UUID id,
                                       @AuthenticationPrincipal String actorId) {
        userService.unlock(id, UUID.fromString(actorId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/lock-status")
    @PreAuthorize("hasAuthority('PERM_usersView')")
    public ResponseEntity<LockStatusResponse> getLockStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getLockStatus(id));
    }
}
