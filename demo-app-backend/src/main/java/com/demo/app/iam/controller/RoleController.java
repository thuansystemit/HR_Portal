package com.demo.app.iam.controller;

import com.demo.app.iam.dto.CreateRoleRequest;
import com.demo.app.iam.dto.PagedResponse;
import com.demo.app.iam.dto.RoleResponse;
import com.demo.app.iam.dto.UpdateRoleRequest;
import com.demo.app.iam.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<PagedResponse<RoleResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(roleService.list(page, size));
    }

    @PostMapping
    public ResponseEntity<RoleResponse> create(@RequestBody @Valid CreateRoleRequest request) {
        var created = roleService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/roles/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(roleService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleResponse> update(@PathVariable UUID id,
                                               @RequestBody @Valid UpdateRoleRequest request) {
        return ResponseEntity.ok(roleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
