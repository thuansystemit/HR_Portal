package com.demo.app.cv.controller;

import com.demo.app.cv.dto.CvCandidateResponse;
import com.demo.app.cv.dto.IngestCvRequest;
import com.demo.app.cv.service.CvCandidateService;
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

    @PostMapping
    public ResponseEntity<CvCandidateResponse> ingest(
            @RequestBody @Valid IngestCvRequest request) {
        var created = cvCandidateService.ingest(request);
        return ResponseEntity
                .created(URI.create("/api/v1/cv-candidates/" + created.id()))
                .body(created);
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        cvCandidateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
