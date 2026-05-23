package com.demo.app.invoice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InvoiceRecordResponse(
        UUID id,
        UUID documentId,
        UUID documentCategoryId,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currency,
        Map<String, Object> vendor,
        Map<String, Object> buyer,
        List<Map<String, Object>> lineItems,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total,
        String notes,
        String paymentTerms,
        String confidenceOverall,
        List<String> lowConfidenceFields,
        List<String> missingFields,
        Instant extractedAt,
        Instant createdAt
) {}
