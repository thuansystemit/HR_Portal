package com.demo.app.content.service;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.platform.metrics.SecurityEventRecorder;
import com.demo.app.content.dto.DocumentResponse;
import com.demo.app.content.dto.DownloadResult;
import com.demo.app.content.entity.Document;
import com.demo.app.content.entity.DocumentType;
import com.demo.app.content.repository.DocumentCategoryRepository;
import com.demo.app.content.repository.DocumentRepository;
import com.demo.app.iam.dto.PagedResponse;
import com.demo.app.iam.repository.UserRepository;
import com.demo.app.insights.service.ReportService;
import com.demo.app.platform.exception.MalwareDetectedException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import com.demo.app.platform.security.malware.MalwareScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentCategoryRepository categoryRepository;
    private final StorageService storageService;
    private final ReportService reportService;
    private final UserRepository userRepository;
    private final MalwareScanService malwareScanService;
    private final AuditService auditService;
    private final SecurityEventRecorder securityEventRecorder;

    @Value("${app.malware.scan.fail-open:false}")
    private boolean failOpen;

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

        // SI-3: Scan file bytes before accepting the upload
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload bytes", e);
        }
        String scanStatus = runMalwareScan(bytes, filename, categoryId, uploadedBy);

        var doc = Document.builder()
                .categoryId(categoryId)
                .name(filename)
                .mimeType(mimeType)
                .storageKey("pending")
                .sizeBytes(file.getSize())
                .uploadedBy(uploadedBy)
                .uploadStatus("committed")
                .scanStatus(scanStatus)
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

        storageService.store(key, bytes);

        category.setDocumentCount(category.getDocumentCount() + 1);
        categoryRepository.save(category);

        scheduleViewRefresh();
        return toResponse(doc);
    }

    /**
     * Runs malware scan and returns the scan status string to record on the Document.
     * Throws MalwareDetectedException if infected.
     * Throws IllegalStateException if scan error and fail-open=false (FedRAMP default).
     */
    private String runMalwareScan(byte[] bytes, String filename, UUID categoryId, UUID uploadedBy) {
        var result = malwareScanService.scan(bytes, filename);
        return switch (result.status()) {
            case CLEAN -> "CLEAN";
            case INFECTED -> {
                securityEventRecorder.recordMalwareBlocked();
                auditService.log(uploadedBy, "DOCUMENT_MALWARE_BLOCKED", "DocumentCategory", categoryId,
                        null, Map.of("filename", filename, "threat", String.valueOf(result.detail())), "blocked");
                throw new MalwareDetectedException(result.detail() != null ? result.detail() : "unknown threat");
            }
            case ERROR -> {
                securityEventRecorder.recordMalwareScanError();
                log.error("Malware scan error for '{}': {}", filename, result.detail());
                if (!failOpen) {
                    throw new IllegalStateException(
                            "Malware scan unavailable — upload rejected (fail-closed). " + result.detail());
                }
                log.warn("Malware scan fail-open: proceeding with unscanned file '{}'", filename);
                yield "SCAN_ERROR";
            }
        };
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
        String uploadedByName = userRepository.findByIdAndDeletedAtIsNull(d.getUploadedBy())
                .map(u -> u.getFullName())
                .orElse("Unknown");
        return new DocumentResponse(d.getId(), d.getCategoryId(), d.getName(),
                d.getMimeType(), d.getSizeBytes(), d.getUploadedBy(), uploadedByName,
                d.getUploadedAt(), d.getExtractionStatus(), d.getScanStatus());
    }
}
