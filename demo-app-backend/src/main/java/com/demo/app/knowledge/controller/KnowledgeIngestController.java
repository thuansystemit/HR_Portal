package com.demo.app.knowledge.controller;

import com.demo.app.knowledge.dto.KnowledgeIngestRequest;
import com.demo.app.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeIngestController {

    private final KnowledgeService knowledgeService;

    @PostMapping("/ingest")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<Void> ingest(@RequestBody @Valid KnowledgeIngestRequest request) {
        knowledgeService.ingest(request);
        return ResponseEntity.ok().build();
    }
}
