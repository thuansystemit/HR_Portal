package com.demo.app.iam.controller;

import com.demo.app.iam.dto.CreateUserRequest;
import com.demo.app.iam.dto.PagedResponse;
import com.demo.app.iam.dto.UpdateUserRequest;
import com.demo.app.iam.dto.UserResponse;
import com.demo.app.iam.service.UserService;
import com.demo.app.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<PagedResponse<UserResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
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
        var created = userService.create(request);
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
                                               @RequestBody @Valid UpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
