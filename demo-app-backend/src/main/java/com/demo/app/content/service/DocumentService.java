package com.demo.app.content.service;

import com.demo.app.content.dto.DocumentResponse;
import com.demo.app.content.dto.DownloadResult;
import com.demo.app.content.entity.Document;
import com.demo.app.content.entity.DocumentType;
import com.demo.app.content.entity.ExtractionStatus;
import com.demo.app.content.repository.DocumentCategoryRepository;
import com.demo.app.content.repository.DocumentRepository;
import com.demo.app.iam.dto.PagedResponse;
import com.demo.app.insights.service.ReportService;
import com.demo.app.platform.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
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

        if (category.getDocumentType() == DocumentType.CV
                || category.getDocumentType() == DocumentType.INVOICE) {
            doc.setExtractionStatus(ExtractionStatus.PENDING);
        }

        doc = documentRepository.save(doc);

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
    public void updateExtractionStatus(UUID documentId, ExtractionStatus newStatus) {
        updateExtractionStatus(documentId, newStatus, null, null);
    }

    @Transactional
    public void updateExtractionStatus(UUID documentId, ExtractionStatus newStatus,
                                        String errorPhase, String errorMessage) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            ExtractionStatus current = doc.getExtractionStatus();
            if (current != null && !current.canTransitionTo(newStatus)) {
                log.warn("Invalid extraction transition {} -> {} for document {}", current, newStatus, documentId);
                return;
            }
            doc.setExtractionStatus(newStatus);
            doc.setExtractionError(errorPhase != null ? "[" + errorPhase + "] " + errorMessage : null);
            if (newStatus == ExtractionStatus.PROCESSING) {
                doc.setExtractionStartedAt(Instant.now());
            } else if (newStatus == ExtractionStatus.SUCCESS || newStatus == ExtractionStatus.FAILED) {
                doc.setExtractionFinishedAt(Instant.now());
            }
            documentRepository.save(doc);
        });
    }

    @Transactional
    public void resetForRetry(UUID documentId) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            if (doc.getExtractionStatus() != ExtractionStatus.FAILED) return;
            doc.setExtractionStatus(ExtractionStatus.PENDING);
            doc.setExtractionError(null);
            doc.setExtractionStartedAt(null);
            doc.setExtractionFinishedAt(null);
            documentRepository.save(doc);
        });
    }

    @Transactional
    public void timeoutStuckProcessing(Instant cutoff) {
        List<Document> stuck = documentRepository
                .findByExtractionStatusAndExtractionStartedAtBefore(ExtractionStatus.PROCESSING, cutoff);
        for (Document doc : stuck) {
            log.warn("Timing out stuck PROCESSING document {} started at {}", doc.getId(), doc.getExtractionStartedAt());
            doc.setExtractionStatus(ExtractionStatus.FAILED);
            doc.setExtractionError("[TIMEOUT] Extraction exceeded 15 minutes");
            doc.setExtractionFinishedAt(Instant.now());
            documentRepository.save(doc);
        }
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
        return new DocumentResponse(
                d.getId(), d.getCategoryId(), d.getName(), d.getMimeType(),
                d.getSizeBytes(), d.getUploadedBy(), d.getUploadedAt(),
                d.getExtractionStatus() != null ? d.getExtractionStatus().name() : null,
                d.getExtractionError(),
                d.getExtractionStartedAt(),
                d.getExtractionFinishedAt()
        );
    }
}
