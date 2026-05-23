package com.demo.app.content.controller;

import com.demo.app.content.dto.DocumentResponse;
import com.demo.app.content.dto.DownloadResult;
import com.demo.app.content.service.DocumentService;
import com.demo.app.iam.dto.PagedResponse;
import com.demo.app.platform.idempotency.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private DocumentController documentController;

    private final UUID CAT_ID = UUID.randomUUID();
    private final UUID DOC_ID = UUID.randomUUID();
    private final UUID USER_ID = UUID.randomUUID();

    private DocumentResponse buildDocumentResponse() {
        return new DocumentResponse(DOC_ID, CAT_ID, "file.pdf", "application/pdf",
                1024L, USER_ID, "Test User", Instant.now(), null);
    }

    @Test
    void list_returnsOk() {
        var docResponse = buildDocumentResponse();
        var paged = new PagedResponse<>(List.of(docResponse), 0, 10, 1L, 1);

        when(documentService.list(CAT_ID, 0, 10)).thenReturn(paged);

        var result = documentController.list(CAT_ID, 0, 10);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().content()).hasSize(1);
    }

    @Test
    void upload_noIdempotencyKey_returnsCreated() {
        var file = mock(MultipartFile.class);
        var docResponse = buildDocumentResponse();
        var userId = USER_ID.toString();

        when(documentService.upload(CAT_ID, USER_ID, file)).thenReturn(docResponse);

        var result = documentController.upload(CAT_ID, userId, file, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getFirst("Location")).contains(DOC_ID.toString());
        verify(idempotencyService, never()).findCached(any(), anyString(), anyString(), any());
    }

    @Test
    void upload_withIdempotencyKey_cacheHit_returnsOkWithReplayHeader() {
        var file = mock(MultipartFile.class);
        var cachedResponse = buildDocumentResponse();
        var userId = USER_ID.toString();

        when(idempotencyService.findCached(USER_ID, "document", "key", DocumentResponse.class))
                .thenReturn(Optional.of(cachedResponse));

        var result = documentController.upload(CAT_ID, userId, file, "key");

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getHeaders().getFirst("X-Idempotent-Replayed")).isEqualTo("true");
        assertThat(result.getBody()).isEqualTo(cachedResponse);
        verify(documentService, never()).upload(any(), any(), any());
    }

    @Test
    void upload_withIdempotencyKey_cacheMiss_returnsCreatedAndStores() {
        var file = mock(MultipartFile.class);
        var docResponse = buildDocumentResponse();
        var userId = USER_ID.toString();

        when(idempotencyService.findCached(USER_ID, "document", "mykey", DocumentResponse.class))
                .thenReturn(Optional.empty());
        when(documentService.upload(CAT_ID, USER_ID, file)).thenReturn(docResponse);

        var result = documentController.upload(CAT_ID, userId, file, "mykey");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(idempotencyService).store(USER_ID, "document", "mykey", docResponse);
    }

    @Test
    void download_returnsOkWithHeaders() {
        var resource = mock(Resource.class);
        var downloadResult = new DownloadResult(resource, "application/pdf", "file.pdf");

        when(documentService.download(DOC_ID)).thenReturn(downloadResult);

        var result = documentController.download(CAT_ID, DOC_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getHeaders().getFirst("Content-Disposition")).contains("file.pdf");
        assertThat(result.getBody()).isEqualTo(resource);
    }

    @Test
    void delete_returns204() {
        var result = documentController.delete(CAT_ID, DOC_ID);

        verify(documentService).delete(DOC_ID);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
