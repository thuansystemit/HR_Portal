package com.demo.app.content.controller;

import com.demo.app.content.dto.CategoryResponse;
import com.demo.app.content.dto.CreateCategoryRequest;
import com.demo.app.content.dto.UpdateCategoryRequest;
import com.demo.app.content.entity.DocumentType;
import com.demo.app.content.service.DocumentCategoryService;
import com.demo.app.iam.entity.UserRole;
import com.demo.app.iam.repository.UserRoleRepository;
import com.demo.app.platform.idempotency.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentCategoryControllerTest {

    @Mock
    private DocumentCategoryService categoryService;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private DocumentCategoryController documentCategoryController;

    private final UUID CAT_ID = UUID.randomUUID();
    private final UUID ROLE_ID = UUID.randomUUID();

    private CategoryResponse buildCategoryResponse() {
        return new CategoryResponse(CAT_ID, "Docs", "desc", 0,
                List.of(), Instant.now(), DocumentType.INVOICE, true);
    }

    @Test
    void list_withNoRole_delegatesNullRoleId() {
        var userId = UUID.randomUUID().toString();
        var categoryResponse = buildCategoryResponse();

        when(userRoleRepository.findByUserId(UUID.fromString(userId))).thenReturn(List.of());
        when(categoryService.listForRole(null)).thenReturn(List.of(categoryResponse));

        var result = documentCategoryController.list(userId);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
        verify(categoryService).listForRole(null);
    }

    @Test
    void list_withRole_delegatesRoleId() {
        var userId = UUID.randomUUID().toString();
        var userRole = UserRole.builder().roleId(ROLE_ID).build();
        var categoryResponse = buildCategoryResponse();

        when(userRoleRepository.findByUserId(UUID.fromString(userId))).thenReturn(List.of(userRole));
        when(categoryService.listForRole(ROLE_ID)).thenReturn(List.of(categoryResponse));

        var result = documentCategoryController.list(userId);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(categoryService).listForRole(ROLE_ID);
    }

    @Test
    void create_noIdempotencyKey_returnsCreated() {
        var request = new CreateCategoryRequest("Docs", "desc", DocumentType.INVOICE, null, null);
        var categoryResponse = buildCategoryResponse();

        when(categoryService.create(request)).thenReturn(categoryResponse);

        var result = documentCategoryController.create(request, null, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getFirst("Location")).contains(CAT_ID.toString());
        verify(idempotencyService, never()).findCached(any(), anyString(), anyString(), any());
    }

    @Test
    void create_withIdempotencyKey_cacheHit_returnsOkWithReplayHeader() {
        var request = new CreateCategoryRequest("Docs", "desc", DocumentType.INVOICE, null, null);
        var cachedResponse = buildCategoryResponse();
        var userId = UUID.randomUUID().toString();

        when(idempotencyService.findCached(UUID.fromString(userId), "category", "key", CategoryResponse.class))
                .thenReturn(Optional.of(cachedResponse));

        var result = documentCategoryController.create(request, userId, "key");

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getHeaders().getFirst("X-Idempotent-Replayed")).isEqualTo("true");
        assertThat(result.getBody()).isEqualTo(cachedResponse);
        verify(categoryService, never()).create(any());
    }

    @Test
    void create_withIdempotencyKey_cacheMiss_returnsCreatedAndStores() {
        var request = new CreateCategoryRequest("Docs", "desc", DocumentType.INVOICE, null, null);
        var categoryResponse = buildCategoryResponse();
        var userId = UUID.randomUUID().toString();

        when(idempotencyService.findCached(UUID.fromString(userId), "category", "mykey", CategoryResponse.class))
                .thenReturn(Optional.empty());
        when(categoryService.create(request)).thenReturn(categoryResponse);

        var result = documentCategoryController.create(request, userId, "mykey");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(idempotencyService).store(UUID.fromString(userId), "category", "mykey", categoryResponse);
    }

    @Test
    void create_withIdempotencyKey_butNullUserId_returnsCreated() {
        // idempotencyKey not null but userId null -> skip idempotency entirely
        var request = new CreateCategoryRequest("Docs", "desc", DocumentType.INVOICE, null, null);
        var categoryResponse = buildCategoryResponse();

        when(categoryService.create(request)).thenReturn(categoryResponse);

        var result = documentCategoryController.create(request, null, "some-key");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(idempotencyService, never()).findCached(any(), anyString(), anyString(), any());
        verify(idempotencyService, never()).store(any(), anyString(), anyString(), any());
    }

    @Test
    void findById_returnsOk() {
        var categoryResponse = buildCategoryResponse();
        when(categoryService.findById(CAT_ID)).thenReturn(categoryResponse);

        var result = documentCategoryController.findById(CAT_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(categoryResponse);
    }

    @Test
    void update_returnsOk() {
        var request = new UpdateCategoryRequest("Docs", "updated desc", DocumentType.INVOICE, null, null);
        var categoryResponse = buildCategoryResponse();

        when(categoryService.update(CAT_ID, request)).thenReturn(categoryResponse);

        var result = documentCategoryController.update(CAT_ID, request);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(categoryResponse);
    }

    @Test
    void delete_returns204() {
        var result = documentCategoryController.delete(CAT_ID);

        verify(categoryService).delete(CAT_ID);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
