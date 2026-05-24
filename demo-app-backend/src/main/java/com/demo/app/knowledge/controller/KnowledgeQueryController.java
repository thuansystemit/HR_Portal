package com.demo.app.knowledge.controller;

import com.demo.app.knowledge.dto.KnowledgeEntityResponse;
import com.demo.app.knowledge.dto.KnowledgeEntitySummary;
import com.demo.app.knowledge.dto.KnowledgeGraphResponse;
import com.demo.app.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeQueryController {

    private final KnowledgeService knowledgeService;

    @GetMapping("/entities")
    public ResponseEntity<Page<KnowledgeEntitySummary>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(knowledgeService.search(q, type, pageable));
    }

    @GetMapping("/entities/{id}")
    public ResponseEntity<KnowledgeEntityResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(knowledgeService.findById(id));
    }

    @GetMapping("/entities/{id}/graph")
    public ResponseEntity<KnowledgeGraphResponse> getGraph(@PathVariable UUID id) {
        return ResponseEntity.ok(knowledgeService.getGraph(id));
    }
}
