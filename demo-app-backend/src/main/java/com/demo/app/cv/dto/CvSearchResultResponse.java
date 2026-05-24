package com.demo.app.cv.dto;

import java.util.List;
import java.util.UUID;

/**
 * Lightweight projection returned by the CV candidate search endpoint.
 * Contains enough data for a search results listing without loading the full candidate graph.
 */
public record CvSearchResultResponse(
        UUID candidateId,
        String fullName,
        String email,
        String city,
        String country,
        String currentTitle,
        double totalExperienceYears,
        List<String> topSkills,
        int relevanceScore,
        UUID documentId,
        UUID documentCategoryId
) {}
