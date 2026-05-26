package com.demo.app.cv.controller;

import com.demo.app.cv.dto.CvCandidateResponse;
import com.demo.app.cv.dto.CvCandidateSimpleResult;
import com.demo.app.cv.dto.IngestCvRequest;
import com.demo.app.cv.dto.UpdateHiringStatusRequest;
import com.demo.app.cv.service.CvCandidateService;
import com.demo.app.recruitment.dto.ApplicationResponse;
import com.demo.app.recruitment.service.JobApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cv-candidates")
@RequiredArgsConstructor
public class CvCandidateController {

    private final CvCandidateService cvCandidateService;
    private final JobApplicationService jobApplicationService;

    @PostMapping
    public ResponseEntity<CvCandidateResponse> ingest(
            @RequestBody @Valid IngestCvRequest request) {
        var created = cvCandidateService.ingest(request);
        if (created == null) {
            // REJECTED or ERROR — document status already set to FAILED, no candidate to return
            return ResponseEntity.ok().build();
        }
        return ResponseEntity
                .created(URI.create("/api/v1/cv-candidates/" + created.id()))
                .body(created);
    }

    @GetMapping("/search-simple")
    public ResponseEntity<List<CvCandidateSimpleResult>> searchSimple(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(cvCandidateService.searchSimple(q, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CvCandidateResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(cvCandidateService.findById(id));
    }

    @GetMapping("/by-document/{documentId}")
    public ResponseEntity<CvCandidateResponse> findByDocumentId(@PathVariable UUID documentId) {
        return ResponseEntity.ok(cvCandidateService.findByDocumentId(documentId));
    }

    @GetMapping("/by-category/{categoryId}")
    public ResponseEntity<List<CvCandidateResponse>> listByCategory(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(cvCandidateService.listByCategory(categoryId));
    }

    @GetMapping("/{id}/applications")
    public ResponseEntity<List<ApplicationResponse>> listApplications(@PathVariable UUID id) {
        return ResponseEntity.ok(jobApplicationService.listByCandidateId(id));
    }

    @PatchMapping("/{id}/hiring-status")
    public ResponseEntity<CvCandidateResponse> updateHiringStatus(
            @PathVariable UUID id,
            @RequestBody UpdateHiringStatusRequest request) {
        return ResponseEntity.ok(cvCandidateService.updateHiringStatus(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        cvCandidateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
