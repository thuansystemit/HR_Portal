package com.demo.app.knowledge.controller;

import com.demo.app.knowledge.dto.KnowledgeIngestRequest;
import com.demo.app.knowledge.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestControllerTest {

    @Mock KnowledgeService knowledgeService;
    @InjectMocks KnowledgeIngestController controller;

    private final UUID DOC_ID = UUID.randomUUID();
    private final UUID CAT_ID = UUID.randomUUID();

    @Test
    void ingest_returnsOk() {
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                "Guide", "A guide", "tutorial", null, null, null);

        var result = controller.ingest(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(knowledgeService).ingest(request);
    }

    @Test
    void ingest_returnsOk_forRejectedStatus() {
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "REJECTED",
                List.of("file too large"), null, null, null, null, null, null);

        var result = controller.ingest(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(knowledgeService).ingest(request);
    }
}
