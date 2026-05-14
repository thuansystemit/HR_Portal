package com.demo.app.content.controller;

import com.demo.app.content.dto.DocumentResponse;
import com.demo.app.content.service.DocumentService;
import com.demo.app.iam.dto.PagedResponse;
import com.demo.app.platform.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories/{categoryId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final IdempotencyService idempotencyService;

    @GetMapping
    public ResponseEntity<PagedResponse<DocumentResponse>> list(
            @PathVariable UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(documentService.list(categoryId, page, size));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal String userId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey != null && userId != null) {
            var cached = idempotencyService.findCached(
                    UUID.fromString(userId), "document", idempotencyKey, DocumentResponse.class);
            if (cached.isPresent()) {
                return ResponseEntity.ok()
                        .header("X-Idempotent-Replayed", "true")
                        .body(cached.get());
            }
        }
        var doc = documentService.upload(categoryId, UUID.fromString(userId), file);
        if (idempotencyKey != null && userId != null) {
            idempotencyService.store(UUID.fromString(userId), "document", idempotencyKey, doc);
        }
        return ResponseEntity.created(
                URI.create("/api/v1/categories/" + categoryId + "/documents/" + doc.id()))
                .body(doc);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable UUID categoryId,
            @PathVariable UUID id) {
        var result = documentService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.filename() + "\"")
                .body(result.resource());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID categoryId, @PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
