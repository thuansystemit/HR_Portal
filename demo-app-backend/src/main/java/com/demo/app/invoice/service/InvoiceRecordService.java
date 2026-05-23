package com.demo.app.invoice.service;

import com.demo.app.content.entity.ExtractionStatus;
import com.demo.app.content.service.DocumentService;
import com.demo.app.invoice.dto.IngestInvoiceRequest;
import com.demo.app.invoice.dto.InvoiceRecordResponse;
import com.demo.app.invoice.entity.InvoiceRecord;
import com.demo.app.invoice.extraction.InvoiceExtractionResult;
import com.demo.app.invoice.repository.InvoiceRecordRepository;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceRecordService {

    private final InvoiceRecordRepository repository;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    @Value("${app.cv.output-dir:/app/output}")
    private String outputDir;

    public InvoiceRecordResponse ingest(IngestInvoiceRequest request) {
        Path jsonPath = Paths.get(outputDir).resolve(request.jsonFile()).normalize();
        if (!jsonPath.startsWith(Paths.get(outputDir).normalize())) {
            throw new IllegalArgumentException("Invalid jsonFile path");
        }
        try {
            byte[] bytes = Files.readAllBytes(jsonPath);
            InvoiceExtractionResult result = objectMapper.readValue(bytes, InvoiceExtractionResult.class);
            InvoiceRecordResponse response = create(result, request.documentId(), request.documentCategoryId());
            documentService.updateExtractionStatus(request.documentId(), ExtractionStatus.SUCCESS);
            return response;
        } catch (IOException e) {
            documentService.updateExtractionStatus(request.documentId(), ExtractionStatus.FAILED,
                    "JSON_READ", e.getMessage());
            throw new RuntimeException("Failed to read extraction result: " + request.jsonFile(), e);
        }
    }

    private InvoiceRecordResponse create(InvoiceExtractionResult result, UUID documentId, UUID documentCategoryId) {
        if (repository.existsByDocumentId(documentId)) {
            throw new ConflictException("Invoice already extracted for document: " + documentId);
        }

        var record = InvoiceRecord.builder()
                .documentId(documentId)
                .documentCategoryId(documentCategoryId)
                .invoiceNumber(result.invoiceNumber())
                .invoiceDate(parseDate(result.invoiceDate()))
                .dueDate(parseDate(result.dueDate()))
                .currency(result.currency())
                .vendor(toMap(result.vendor()))
                .buyer(toMap(result.buyer()))
                .lineItems(toListOfMaps(result.lineItems()))
                .subtotal(result.subtotal())
                .taxAmount(result.taxAmount())
                .total(result.total())
                .notes(result.notes())
                .paymentTerms(result.paymentTerms())
                .confidenceOverall(normalizeConfidence(result.confidenceOverall()))
                .lowConfidenceFields(result.lowConfidenceFields())
                .missingFields(result.missingFields())
                .build();

        var saved = repository.save(record);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public InvoiceRecordResponse findById(UUID id) {
        var record = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InvoiceRecord", id));
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public InvoiceRecordResponse findByDocumentId(UUID documentId) {
        var record = repository.findByDocumentId(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "InvoiceRecord not found for documentId: " + documentId));
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public List<InvoiceRecordResponse> listByCategory(UUID categoryId) {
        return repository.findByDocumentCategoryId(categoryId).stream()
                .map(this::toResponse)
                .toList();
    }

    public void delete(UUID id) {
        var record = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InvoiceRecord", id));
        repository.delete(record);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeConfidence(String value) {
        if (value == null) return "LOW";
        return switch (value.toUpperCase()) {
            case "HIGH" -> "HIGH";
            case "MEDIUM" -> "MEDIUM";
            default -> "LOW";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        if (obj == null) return null;
        return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toListOfMaps(List<?> list) {
        if (list == null) return null;
        return objectMapper.convertValue(list, new TypeReference<List<Map<String, Object>>>() {});
    }

    private InvoiceRecordResponse toResponse(InvoiceRecord r) {
        return new InvoiceRecordResponse(
                r.getId(), r.getDocumentId(), r.getDocumentCategoryId(),
                r.getInvoiceNumber(), r.getInvoiceDate(), r.getDueDate(),
                r.getCurrency(), r.getVendor(), r.getBuyer(), r.getLineItems(),
                r.getSubtotal(), r.getTaxAmount(), r.getTotal(),
                r.getNotes(), r.getPaymentTerms(),
                r.getConfidenceOverall(), r.getLowConfidenceFields(), r.getMissingFields(),
                r.getExtractedAt(), r.getCreatedAt());
    }
}
