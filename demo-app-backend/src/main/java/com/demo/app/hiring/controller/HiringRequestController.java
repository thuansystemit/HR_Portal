package com.demo.app.hiring.controller;

import com.demo.app.hiring.dto.CreateHiringRequestRequest;
import com.demo.app.hiring.dto.HiringRequestResponse;
import com.demo.app.hiring.dto.UpdateHiringRequestStatusRequest;
import com.demo.app.hiring.service.HiringRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hiring-requests")
@RequiredArgsConstructor
public class HiringRequestController {

    private final HiringRequestService hiringRequestService;

    @PostMapping
    public ResponseEntity<HiringRequestResponse> create(
            @RequestBody @Valid CreateHiringRequestRequest request,
            Authentication authentication) {
        UUID requesterId = UUID.fromString(authentication.getName());
        HiringRequestResponse response = hiringRequestService.create(request, requesterId);
        return ResponseEntity.created(URI.create("/api/v1/hiring-requests/" + response.id()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<HiringRequestResponse>> listAll() {
        return ResponseEntity.ok(hiringRequestService.listAll());
    }

    @GetMapping("/my")
    public ResponseEntity<List<HiringRequestResponse>> listMine(Authentication authentication) {
        UUID requesterId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(hiringRequestService.listByRequester(requesterId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HiringRequestResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(hiringRequestService.getById(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<HiringRequestResponse> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateHiringRequestStatusRequest request,
            Authentication authentication) {
        UUID updatedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(hiringRequestService.updateStatus(id, request, updatedBy));
    }
}
