package com.demo.app.hiring.controller;

import com.demo.app.hiring.dto.CvShareResponse;
import com.demo.app.hiring.dto.ShareCvRequest;
import com.demo.app.hiring.dto.SubmitImpressionRequest;
import com.demo.app.hiring.service.CvShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CvShareController {

    private final CvShareService cvShareService;

    @PostMapping("/api/v1/hiring-requests/{requestId}/cv-shares")
    public ResponseEntity<CvShareResponse> share(
            @PathVariable UUID requestId,
            @RequestBody @Valid ShareCvRequest request,
            Authentication authentication) {
        UUID sharedBy = UUID.fromString(authentication.getName());
        CvShareResponse response = cvShareService.share(requestId, request, sharedBy);
        return ResponseEntity.created(
                URI.create("/api/v1/hiring-requests/" + requestId + "/cv-shares/" + response.id()))
                .body(response);
    }

    @GetMapping("/api/v1/hiring-requests/{requestId}/cv-shares")
    public ResponseEntity<List<CvShareResponse>> listByRequest(@PathVariable UUID requestId) {
        return ResponseEntity.ok(cvShareService.listByRequest(requestId));
    }

    @GetMapping("/api/v1/cv-shares/{shareId}")
    public ResponseEntity<CvShareResponse> getById(@PathVariable UUID shareId) {
        return ResponseEntity.ok(cvShareService.getById(shareId));
    }

    @GetMapping("/api/v1/cv-shares/inbox")
    public ResponseEntity<List<CvShareResponse>> inbox(Authentication authentication) {
        UUID sharedWith = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(cvShareService.listMySharedCvs(sharedWith));
    }

    @PatchMapping("/api/v1/hiring-requests/{requestId}/cv-shares/{shareId}/impression")
    public ResponseEntity<CvShareResponse> submitImpression(
            @PathVariable UUID requestId,
            @PathVariable UUID shareId,
            @RequestBody @Valid SubmitImpressionRequest request,
            Authentication authentication) {
        UUID reviewerId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(cvShareService.submitImpression(shareId, request, reviewerId));
    }
}
