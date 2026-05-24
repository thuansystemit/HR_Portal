package com.demo.app.recruitment.controller;

import com.demo.app.recruitment.dto.InterviewResponse;
import com.demo.app.recruitment.dto.ScheduleInterviewRequest;
import com.demo.app.recruitment.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruitment/applications/{appId}")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping("/interviews")
    public ResponseEntity<InterviewResponse> schedule(
            @PathVariable UUID appId,
            @RequestBody @Valid ScheduleInterviewRequest request,
            Authentication authentication) {
        var createdBy = UUID.fromString(authentication.getName());
        var response = interviewService.schedule(appId, request, createdBy);
        return ResponseEntity.created(URI.create(
                "/api/v1/recruitment/applications/" + appId + "/interviews/" + response.id()))
                .body(response);
    }

    @GetMapping("/interviews")
    public ResponseEntity<List<InterviewResponse>> list(@PathVariable UUID appId) {
        return ResponseEntity.ok(interviewService.listByApplication(appId));
    }
}
