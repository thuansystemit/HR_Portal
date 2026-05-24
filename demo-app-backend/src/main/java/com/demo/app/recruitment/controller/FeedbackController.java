package com.demo.app.recruitment.controller;

import com.demo.app.recruitment.dto.FeedbackResponse;
import com.demo.app.recruitment.dto.SubmitFeedbackRequest;
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
@RequestMapping("/api/v1/recruitment/interviews/{interviewId}")
@RequiredArgsConstructor
public class FeedbackController {

    private final InterviewService interviewService;

    @PostMapping("/feedback")
    public ResponseEntity<FeedbackResponse> submitFeedback(
            @PathVariable UUID interviewId,
            @RequestBody @Valid SubmitFeedbackRequest request,
            Authentication authentication) {
        var reviewerId = UUID.fromString(authentication.getName());
        var response = interviewService.submitFeedback(interviewId, request, reviewerId);
        return ResponseEntity.created(URI.create(
                "/api/v1/recruitment/interviews/" + interviewId + "/feedback/" + response.id()))
                .body(response);
    }

    @GetMapping("/feedback")
    public ResponseEntity<List<FeedbackResponse>> listFeedback(@PathVariable UUID interviewId) {
        return ResponseEntity.ok(interviewService.listFeedback(interviewId));
    }
}
