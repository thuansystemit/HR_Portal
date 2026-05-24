package com.demo.app.content.service;

import com.demo.app.content.entity.Document;
import com.demo.app.content.entity.DocumentCategory;
import com.demo.app.content.entity.DocumentType;
import com.demo.app.content.repository.DocumentCategoryRepository;
import com.demo.app.content.repository.DocumentRepository;
import com.demo.app.iam.repository.UserRepository;
import com.demo.app.insights.service.ReportService;
import com.demo.app.platform.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock DocumentRepository documentRepository;
    @Mock DocumentCategoryRepository categoryRepository;
    @Mock StorageService storageService;
    @Mock ReportService reportService;
    @Mock UserRepository userRepository;

    @InjectMocks
    DocumentService documentService;

    private final UUID CAT_ID = UUID.randomUUID();
    private final UUID DOC_ID = UUID.randomUUID();
    private final UUID USER_ID = UUID.randomUUID();

    @Test
    void list_returnsPaged() {
        var doc = buildDocument();
        when(documentRepository.findByCategoryIdAndDeletedAtIsNullAndUploadStatus(
                eq(CAT_ID), eq("committed"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doc)));

        var result = documentService.list(CAT_ID, 0, 10);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    void upload_succeeds_forNonCvType() throws IOException {
        var category = buildCategory(DocumentType.INVOICE);
        var doc = buildDocument();
        var file = mock(MultipartFile.class);

        try (MockedStatic<TransactionSynchronizationManager> txMgr =
                     mockStatic(TransactionSynchronizationManager.class)) {
            txMgr.when(() -> TransactionSynchronizationManager.registerSynchronization(any(TransactionSynchronization.class)))
                 .thenAnswer(inv -> null);

            when(categoryRepository.findByIdAndDeletedAtIsNull(CAT_ID)).thenReturn(Optional.of(category));
            when(file.getOriginalFilename()).thenReturn("test.pdf");
            when(file.getContentType()).thenReturn("application/pdf");
            when(file.getSize()).thenReturn(1024L);
            when(documentRepository.save(any())).thenReturn(doc);
            when(categoryRepository.save(any())).thenReturn(category);

            var result = documentService.upload(CAT_ID, USER_ID, file);

            assertThat(result).isNotNull();
            verify(storageService).store(anyString(), eq(file));
            assertThat(doc.getExtractionStatus()).isNull();
        }
    }

    @Test
    void upload_setsPendingExtraction_forCvType() throws IOException {
        var category = buildCategory(DocumentType.CV);
        var file = mock(MultipartFile.class);

        try (MockedStatic<TransactionSynchronizationManager> txMgr =
                     mockStatic(TransactionSynchronizationManager.class)) {
            txMgr.when(() -> TransactionSynchronizationManager.registerSynchronization(any(TransactionSynchronization.class)))
                 .thenAnswer(inv -> null);

            when(categoryRepository.findByIdAndDeletedAtIsNull(CAT_ID)).thenReturn(Optional.of(category));
            when(file.getOriginalFilename()).thenReturn("cv.pdf");
            when(file.getContentType()).thenReturn("application/pdf");
            when(file.getSize()).thenReturn(512L);
            when(documentRepository.save(any())).thenAnswer(inv -> {
                Document d = inv.getArgument(0);
                d.setId(DOC_ID);
                return d;
            });
            when(categoryRepository.save(any())).thenReturn(category);

            documentService.upload(CAT_ID, USER_ID, file);

            verify(documentRepository, atLeastOnce()).save(argThat(d -> "PENDING".equals(d.getExtractionStatus())));
        }
    }

    @Test
    void upload_throws_whenCategoryNotFound() {
        var file = mock(MultipartFile.class);
        when(categoryRepository.findByIdAndDeletedAtIsNull(CAT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.upload(CAT_ID, USER_ID, file))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void download_returnsResource() {
        var doc = buildDocument();
        when(documentRepository.findByIdAndDeletedAtIsNull(DOC_ID)).thenReturn(Optional.of(doc));
        when(storageService.load(doc.getStorageKey())).thenReturn(mock(org.springframework.core.io.Resource.class));

        var result = documentService.download(DOC_ID);

        assertThat(result).isNotNull();
        assertThat(result.mimeType()).isEqualTo("application/pdf");
        assertThat(result.filename()).isEqualTo("test.pdf");
    }

    @Test
    void download_throws_whenNotFound() {
        when(documentRepository.findByIdAndDeletedAtIsNull(DOC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.download(DOC_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_softDeletesAndDecrementsCount() {
        var doc = buildDocument();
        var category = buildCategory(DocumentType.INVOICE);
        category.setDocumentCount(5);

        try (MockedStatic<TransactionSynchronizationManager> txMgr =
                     mockStatic(TransactionSynchronizationManager.class)) {
            txMgr.when(() -> TransactionSynchronizationManager.registerSynchronization(any(TransactionSynchronization.class)))
                 .thenAnswer(inv -> null);

            when(documentRepository.findByIdAndDeletedAtIsNull(DOC_ID)).thenReturn(Optional.of(doc));
            when(documentRepository.save(any())).thenReturn(doc);
            when(categoryRepository.findByIdAndDeletedAtIsNull(CAT_ID)).thenReturn(Optional.of(category));
            when(categoryRepository.save(any())).thenReturn(category);

            documentService.delete(DOC_ID);

            assertThat(doc.getDeletedAt()).isNotNull();
            verify(storageService).delete(doc.getStorageKey());
            assertThat(category.getDocumentCount()).isEqualTo(4);
        }
    }

    @Test
    void delete_throws_whenNotFound() {
        when(documentRepository.findByIdAndDeletedAtIsNull(DOC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.delete(DOC_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateExtractionStatus_updatesStatus() {
        var doc = buildDocument();
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);

        documentService.updateExtractionStatus(DOC_ID, "COMPLETED");

        assertThat(doc.getExtractionStatus()).isEqualTo("COMPLETED");
        assertThat(doc.getExtractionError()).isNull();
    }

    @Test
    void updateExtractionStatus_updatesStatusWithError() {
        var doc = buildDocument();
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);

        documentService.updateExtractionStatus(DOC_ID, "FAILED", "JSON_READ", "parse error");

        assertThat(doc.getExtractionStatus()).isEqualTo("FAILED");
        assertThat(doc.getExtractionError()).contains("JSON_READ").contains("parse error");
    }

    private Document buildDocument() {
        return Document.builder()
                .id(DOC_ID)
                .categoryId(CAT_ID)
                .name("test.pdf")
                .mimeType("application/pdf")
                .storageKey("general/" + CAT_ID + "/" + DOC_ID + "/test.pdf")
                .sizeBytes(1024L)
                .uploadedBy(USER_ID)
                .uploadStatus("committed")
                .uploadedAt(Instant.now())
                .build();
    }

    @Test
    void delete_doesNotDecrementCount_whenCountIsZero() {
        var doc = buildDocument();
        var category = buildCategory(DocumentType.INVOICE);
        category.setDocumentCount(0);

        try (MockedStatic<TransactionSynchronizationManager> txMgr =
                     mockStatic(TransactionSynchronizationManager.class)) {
            txMgr.when(() -> TransactionSynchronizationManager.registerSynchronization(any(TransactionSynchronization.class)))
                 .thenAnswer(inv -> null);

            when(documentRepository.findByIdAndDeletedAtIsNull(DOC_ID)).thenReturn(Optional.of(doc));
            when(documentRepository.save(any())).thenReturn(doc);
            when(categoryRepository.findByIdAndDeletedAtIsNull(CAT_ID)).thenReturn(Optional.of(category));

            documentService.delete(DOC_ID);

            assertThat(doc.getDeletedAt()).isNotNull();
            // count should stay 0 — categoryRepository.save should NOT be called with a decremented count
            verify(categoryRepository, never()).save(any());
        }
    }

    @Test
    void upload_usesDefaults_whenFilenameAndContentTypeNull() throws IOException {
        var category = buildCategory(DocumentType.INVOICE);
        var doc = buildDocument();
        var file = mock(MultipartFile.class);

        try (MockedStatic<TransactionSynchronizationManager> txMgr =
                     mockStatic(TransactionSynchronizationManager.class)) {
            txMgr.when(() -> TransactionSynchronizationManager.registerSynchronization(any(TransactionSynchronization.class)))
                 .thenAnswer(inv -> null);

            when(categoryRepository.findByIdAndDeletedAtIsNull(CAT_ID)).thenReturn(Optional.of(category));
            when(file.getOriginalFilename()).thenReturn(null);
            when(file.getContentType()).thenReturn(null);
            when(file.getSize()).thenReturn(512L);
            when(documentRepository.save(any())).thenReturn(doc);
            when(categoryRepository.save(any())).thenReturn(category);

            var result = documentService.upload(CAT_ID, USER_ID, file);

            assertThat(result).isNotNull();
            verify(storageService).store(anyString(), eq(file));
        }
    }

    @Test
    void updateExtractionStatus_noOp_whenDocumentNotFound() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.empty());

        // Should not throw, just silently skip
        documentService.updateExtractionStatus(DOC_ID, "COMPLETED");

        verify(documentRepository, never()).save(any());
    }

    @Test
    void updateExtractionStatus_withError_noOp_whenDocumentNotFound() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.empty());

        // Should not throw, just silently skip
        documentService.updateExtractionStatus(DOC_ID, "FAILED", "JSON_READ", "parse error");

        verify(documentRepository, never()).save(any());
    }

    private DocumentCategory buildCategory(DocumentType type) {
        return DocumentCategory.builder()
                .id(CAT_ID)
                .name("TestCat")
                .documentType(type)
                .documentCount(0)
                .createdAt(Instant.now())
                .build();
    }
}
