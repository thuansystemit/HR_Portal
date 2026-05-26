package com.demo.app.cv.controller;

import com.demo.app.cv.dto.CvSearchCriteria;
import com.demo.app.cv.dto.CvSearchResultResponse;
import com.demo.app.cv.service.CvSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvSearchControllerTest {

    @Mock
    CvSearchService searchService;

    @InjectMocks
    CvSearchController controller;

    private CvSearchResultResponse buildResult(String name, int score) {
        return new CvSearchResultResponse(
                UUID.randomUUID(), name, name.toLowerCase().replace(" ", "") + "@example.com",
                "Hanoi", "Vietnam", "Senior Developer", 5.0,
                List.of("Java", "Spring"), score,
                UUID.randomUUID(), UUID.randomUUID(), false
        );
    }

    @Test
    void search_withSkillsAndTitle_returnsOkWithResults() {
        var result = buildResult("John Doe", 85);
        var page = new PageImpl<>(List.of(result), PageRequest.of(0, 20), 1);
        when(searchService.search(any(CvSearchCriteria.class))).thenReturn(page);

        var response = controller.search("Java,Spring", "Developer", null, null, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
        assertThat(response.getBody().getContent().get(0).fullName()).isEqualTo("John Doe");
        assertThat(response.getBody().getContent().get(0).relevanceScore()).isEqualTo(85);
        verify(searchService).search(any(CvSearchCriteria.class));
    }

    @Test
    void search_withNoCriteria_returnsOkWithAllCandidates() {
        var page = new PageImpl<CvSearchResultResponse>(List.of(), PageRequest.of(0, 20), 0);
        when(searchService.search(any(CvSearchCriteria.class))).thenReturn(page);

        var response = controller.search(null, null, null, null, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalElements()).isEqualTo(0);
    }

    @Test
    void search_withPaginationParams_passesCorrectPageToService() {
        var page = new PageImpl<CvSearchResultResponse>(List.of(), PageRequest.of(2, 15), 0);
        when(searchService.search(any(CvSearchCriteria.class))).thenReturn(page);

        var response = controller.search(null, null, null, null, null, 2, 15, "fullName", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(searchService).search(any(CvSearchCriteria.class));
    }

    @Test
    void search_withMinYearsExperienceAndKeyword_returnsOk() {
        var page = new PageImpl<CvSearchResultResponse>(List.of(), PageRequest.of(0, 20), 0);
        when(searchService.search(any(CvSearchCriteria.class))).thenReturn(page);

        var response = controller.search(null, null, null, 5, "cloud", null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(searchService).search(any(CvSearchCriteria.class));
    }

    @Test
    void search_withLocation_returnsOk() {
        var result = buildResult("Alice Nguyen", 60);
        var page = new PageImpl<>(List.of(result), PageRequest.of(0, 20), 1);
        when(searchService.search(any(CvSearchCriteria.class))).thenReturn(page);

        var response = controller.search(null, null, "Vietnam", null, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent().get(0).country()).isEqualTo("Vietnam");
    }

    @Test
    void search_withForJobPostingId_passesIdToService() {
        UUID jobPostingId = UUID.randomUUID();
        var result = new CvSearchResultResponse(
                UUID.randomUUID(), "Bob Smith", "bob@example.com",
                "Hanoi", "Vietnam", "Developer", 3.0,
                List.of("Java"), 70, UUID.randomUUID(), UUID.randomUUID(), true
        );
        var page = new PageImpl<>(List.of(result), PageRequest.of(0, 20), 1);
        when(searchService.search(any(CvSearchCriteria.class))).thenReturn(page);

        var response = controller.search(null, null, null, null, null, null, null, null, jobPostingId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent().get(0).alreadyApplied()).isTrue();
        verify(searchService).search(any(CvSearchCriteria.class));
    }
}
