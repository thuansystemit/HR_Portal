package com.demo.app.recruitment.controller;

import com.demo.app.recruitment.dto.*;
import com.demo.app.recruitment.service.JobPostingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruitment/job-postings")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingService jobPostingService;

    @PostMapping
    public ResponseEntity<JobPostingResponse> create(
            @RequestBody @Valid CreateJobPostingRequest request,
            Authentication authentication) {
        var createdBy = UUID.fromString(authentication.getName());
        var response = jobPostingService.create(request, createdBy);
        return ResponseEntity.created(URI.create("/api/v1/recruitment/job-postings/" + response.id()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<Page<JobPostingSummary>> list(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(jobPostingService.list(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobPostingResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(jobPostingService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobPostingResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateJobPostingRequest request) {
        return ResponseEntity.ok(jobPostingService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        jobPostingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/skills")
    public ResponseEntity<List<JobPostingSkillDto>> getSkills(@PathVariable UUID id) {
        return ResponseEntity.ok(jobPostingService.getSkills(id));
    }

    @PutMapping("/{id}/skills")
    public ResponseEntity<List<JobPostingSkillDto>> setSkills(
            @PathVariable UUID id,
            @RequestBody List<JobPostingSkillDto> skills) {
        return ResponseEntity.ok(jobPostingService.setSkills(id, skills));
    }
}
