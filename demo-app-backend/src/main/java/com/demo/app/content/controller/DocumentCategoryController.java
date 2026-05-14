package com.demo.app.content.controller;

import com.demo.app.content.dto.CategoryResponse;
import com.demo.app.content.dto.CreateCategoryRequest;
import com.demo.app.content.dto.UpdateCategoryRequest;
import com.demo.app.content.service.DocumentCategoryService;
import com.demo.app.iam.repository.UserRoleRepository;
import com.demo.app.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class DocumentCategoryController {

    private final DocumentCategoryService categoryService;
    private final UserRoleRepository userRoleRepository;
    private final IdempotencyService idempotencyService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> list(@AuthenticationPrincipal String userId) {
        var roles = userRoleRepository.findByUserId(UUID.fromString(userId));
        var roleId = roles.isEmpty() ? null : roles.get(0).getRoleId();
        return ResponseEntity.ok(categoryService.listForRole(roleId));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @RequestBody @Valid CreateCategoryRequest request,
            @AuthenticationPrincipal String userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey != null && userId != null) {
            var cached = idempotencyService.findCached(
                    UUID.fromString(userId), "category", idempotencyKey, CategoryResponse.class);
            if (cached.isPresent()) {
                return ResponseEntity.ok()
                        .header("X-Idempotent-Replayed", "true")
                        .body(cached.get());
            }
        }
        var created = categoryService.create(request);
        if (idempotencyKey != null && userId != null) {
            idempotencyService.store(UUID.fromString(userId), "category", idempotencyKey, created);
        }
        return ResponseEntity.created(URI.create("/api/v1/categories/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(@PathVariable UUID id,
                                                   @RequestBody @Valid UpdateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
