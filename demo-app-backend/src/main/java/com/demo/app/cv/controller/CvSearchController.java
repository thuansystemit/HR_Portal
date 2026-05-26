package com.demo.app.cv.controller;

import com.demo.app.cv.dto.CvSearchCriteria;
import com.demo.app.cv.dto.CvSearchResultResponse;
import com.demo.app.cv.service.CvSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cv-candidates/search")
@RequiredArgsConstructor
public class CvSearchController {

    private final CvSearchService cvSearchService;

    /**
     * Search CV candidates with multi-criteria matching and relevance scoring.
     * <p>
     * All parameters are optional. When no criteria are provided, all candidates
     * are returned sorted by name with a relevance score of 0.
     *
     * @param skills               comma-separated skill names (partial match via ILIKE)
     * @param title                job title to match against work experience (ILIKE)
     * @param location             city or country to match (ILIKE)
     * @param minYearsExperience   minimum total years of work experience
     * @param keyword              free-text search across name, summary, companies, titles, skills
     * @param page                 zero-based page number (default 0)
     * @param size                 page size (default 20, max 100)
     * @param sortBy               sort field: relevanceScore (default), fullName, experienceYears
     * @return paginated search results with relevance scores
     */
    @GetMapping
    public ResponseEntity<Page<CvSearchResultResponse>> search(
            @RequestParam(required = false) String skills,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer minYearsExperience,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) UUID forJobPostingId
    ) {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                skills, title, location, minYearsExperience, keyword,
                page, size, sortBy, forJobPostingId
        );
        Page<CvSearchResultResponse> results = cvSearchService.search(criteria);
        return ResponseEntity.ok(results);
    }
}
