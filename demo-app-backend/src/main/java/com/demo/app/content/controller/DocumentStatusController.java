package com.demo.app.content.controller;

import com.demo.app.content.dto.UpdateExtractionStatusRequest;
import com.demo.app.content.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentStatusController {

    private final DocumentService documentService;

    @PatchMapping("/{id}/extraction-status")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateExtractionStatusRequest request) {
        documentService.updateExtractionStatus(id, request.status(),
                request.errorPhase(), request.errorMessage());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/retry-extraction")
    public ResponseEntity<Void> retry(@PathVariable UUID id) {
        documentService.resetForRetry(id);
        return ResponseEntity.noContent().build();
    }
}
