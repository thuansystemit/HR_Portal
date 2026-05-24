package com.demo.app.knowledge.controller;

import com.demo.app.knowledge.dto.KnowledgeEntityResponse;
import com.demo.app.knowledge.dto.KnowledgeEntitySummary;
import com.demo.app.knowledge.dto.KnowledgeGraphResponse;
import com.demo.app.knowledge.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeQueryControllerTest {

    @Mock KnowledgeService knowledgeService;
    @InjectMocks KnowledgeQueryController controller;

    private final UUID ENTITY_ID = UUID.randomUUID();
    private final UUID DOC_ID = UUID.randomUUID();

    private KnowledgeEntitySummary buildSummary() {
        return new KnowledgeEntitySummary(ENTITY_ID, DOC_ID, "Technology", "Spring Boot",
                List.of("Spring"), Instant.now());
    }

    private KnowledgeEntityResponse buildResponse() {
        return new KnowledgeEntityResponse(ENTITY_ID, DOC_ID, "Technology", "Spring Boot",
                List.of("Spring"), null, Instant.now(), List.of(), List.of());
    }

    @Test
    void search_noParams_returnsPage() {
        var page = new PageImpl<>(List.of(buildSummary()));
        when(knowledgeService.search(eq(null), eq(null), any())).thenReturn(page);

        var result = controller.search(null, null, 0, 20);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
    }

    @Test
    void search_withQuery_returnsFilteredPage() {
        var page = new PageImpl<>(List.of(buildSummary()));
        when(knowledgeService.search(eq("spring"), eq("Technology"), any())).thenReturn(page);

        var result = controller.search("spring", "Technology", 0, 20);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(knowledgeService).search("spring", "Technology", PageRequest.of(0, 20));
    }

    @Test
    void findById_returnsEntity() {
        when(knowledgeService.findById(ENTITY_ID)).thenReturn(buildResponse());

        var result = controller.findById(ENTITY_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().name()).isEqualTo("Spring Boot");
    }

    @Test
    void getGraph_returnsGraph() {
        var graph = new KnowledgeGraphResponse(List.of(), List.of());
        when(knowledgeService.getGraph(ENTITY_ID)).thenReturn(graph);

        var result = controller.getGraph(ENTITY_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
    }
}
