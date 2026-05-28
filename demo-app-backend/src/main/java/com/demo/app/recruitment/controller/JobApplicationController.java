package com.demo.app.recruitment.controller;

import com.demo.app.recruitment.dto.*;
import com.demo.app.recruitment.service.JobApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruitment/job-postings/{jobId}")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    @PostMapping("/applications")
    public ResponseEntity<ApplicationResponse> apply(
            @PathVariable UUID jobId,
            @RequestBody @Valid CreateApplicationRequest request,
            Authentication authentication) {
        var moverId = UUID.fromString(authentication.getName());
        var response = jobApplicationService.apply(jobId, request, moverId);
        return ResponseEntity.created(URI.create(
                "/api/v1/recruitment/job-postings/" + jobId + "/applications/" + response.id()))
                .body(response);
    }

    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationResponse>> list(@PathVariable UUID jobId) {
        return ResponseEntity.ok(jobApplicationService.listByPosting(jobId));
    }

    @GetMapping("/applications/board")
    public ResponseEntity<BoardResponse> board(@PathVariable UUID jobId) {
        return ResponseEntity.ok(jobApplicationService.getBoard(jobId));
    }

    @PostMapping("/applications/batch")
    public ResponseEntity<BatchApplyResult> batchApply(
            @PathVariable UUID jobId,
            @RequestBody @Valid BatchApplyRequest request,
            Authentication authentication) {
        var moverId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(jobApplicationService.batchApply(jobId, request, moverId));
    }

    @PatchMapping("/applications/{appId}/stage")
    public ResponseEntity<ApplicationResponse> moveStage(
            @PathVariable UUID jobId,
            @PathVariable UUID appId,
            @RequestBody @Valid MoveStageRequest request,
            Authentication authentication) {
        var moverId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(jobApplicationService.moveStage(jobId, appId, request, moverId));
    }
}
