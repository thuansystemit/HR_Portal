package com.demo.app.content.service;

import com.demo.app.content.dto.DocumentResponse;
import com.demo.app.content.dto.DownloadResult;
import com.demo.app.content.entity.Document;
import com.demo.app.content.entity.DocumentType;
import com.demo.app.content.repository.DocumentCategoryRepository;
import com.demo.app.content.repository.DocumentRepository;
import com.demo.app.iam.dto.PagedResponse;
import com.demo.app.insights.service.ReportService;
import com.demo.app.platform.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentCategoryRepository categoryRepository;
    private final StorageService storageService;
    private final ReportService reportService;

    @Transactional(readOnly = true)
    public PagedResponse<DocumentResponse> list(UUID categoryId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("uploadedAt").descending());
        var p = documentRepository.findByCategoryIdAndDeletedAtIsNullAndUploadStatus(
                categoryId, "committed", pageable);
        var content = p.getContent().stream().map(this::toResponse).toList();
        return new PagedResponse<>(content, page, size, p.getTotalElements(), p.getTotalPages());
    }

    @Transactional
    public DocumentResponse upload(UUID categoryId, UUID uploadedBy, MultipartFile file) {
        var category = categoryRepository.findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentCategory", categoryId));

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        var doc = Document.builder()
                .categoryId(categoryId)
                .name(filename)
                .mimeType(mimeType)
                .storageKey("pending")
                .sizeBytes(file.getSize())
                .uploadedBy(uploadedBy)
                .uploadStatus("committed")
                .build();

        if (category.getDocumentType() == DocumentType.CV) {
            doc.setExtractionStatus("PENDING");
        }

        doc = documentRepository.save(doc);

        // Prefix with lowercase document type so cv-batch-extractor watches only the cv/ subtree
        String typePrefix = category.getDocumentType().name().toLowerCase();
        String key = typePrefix + "/" + categoryId + "/" + doc.getId() + "/" + filename;
        doc.setStorageKey(key);
        doc = documentRepository.save(doc);

        storageService.store(key, file);

        category.setDocumentCount(category.getDocumentCount() + 1);
        categoryRepository.save(category);

        scheduleViewRefresh();
        return toResponse(doc);
    }

    @Transactional(readOnly = true)
    public DownloadResult download(UUID id) {
        var doc = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        var resource = storageService.load(doc.getStorageKey());
        return new DownloadResult(resource, doc.getMimeType(), doc.getName());
    }

    @Transactional
    public void delete(UUID id) {
        var doc = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        doc.setDeletedAt(Instant.now());
        documentRepository.save(doc);

        storageService.delete(doc.getStorageKey());

        categoryRepository.findByIdAndDeletedAtIsNull(doc.getCategoryId()).ifPresent(cat -> {
            if (cat.getDocumentCount() > 0) {
                cat.setDocumentCount(cat.getDocumentCount() - 1);
                categoryRepository.save(cat);
            }
        });

        scheduleViewRefresh();
    }

    @Transactional
    public void updateExtractionStatus(UUID documentId, String status) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setExtractionStatus(status);
            doc.setExtractionError(null);
            documentRepository.save(doc);
        });
    }

    @Transactional
    public void updateExtractionStatus(UUID documentId, String status, String errorPhase, String errorMessage) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setExtractionStatus(status);
            doc.setExtractionError("[" + errorPhase + "] " + errorMessage);
            documentRepository.save(doc);
        });
    }

    private void scheduleViewRefresh() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reportService.refreshMaterializedViews();
            }
        });
    }

    private DocumentResponse toResponse(Document d) {
        return new DocumentResponse(d.getId(), d.getCategoryId(), d.getName(),
                d.getMimeType(), d.getSizeBytes(), d.getUploadedBy(), d.getUploadedAt());
    }
}
