package com.demo.app.invoice.extraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InvoiceExtractionResult(
        String invoiceNumber,
        String invoiceDate,
        String dueDate,
        String currency,
        Party vendor,
        Party buyer,
        List<LineItem> lineItems,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total,
        String notes,
        String paymentTerms,
        String confidenceOverall,
        List<String> lowConfidenceFields,
        List<String> missingFields
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Party(
            String name,
            String address,
            String taxId,
            String email,
            String phone
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineItem(
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount,
            BigDecimal taxRate
    ) {}
}
