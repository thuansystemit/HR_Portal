package com.demo.app.invoice.controller;

import com.demo.app.invoice.dto.IngestInvoiceRequest;
import com.demo.app.invoice.dto.InvoiceRecordResponse;
import com.demo.app.invoice.service.InvoiceRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoice-records")
@RequiredArgsConstructor
public class InvoiceRecordController {

    private final InvoiceRecordService invoiceRecordService;

    @PostMapping
    public ResponseEntity<InvoiceRecordResponse> ingest(
            @RequestBody @Valid IngestInvoiceRequest request) {
        var created = invoiceRecordService.ingest(request);
        return ResponseEntity
                .created(URI.create("/api/v1/invoice-records/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceRecordResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceRecordService.findById(id));
    }

    @GetMapping("/by-document/{documentId}")
    public ResponseEntity<InvoiceRecordResponse> findByDocumentId(@PathVariable UUID documentId) {
        return ResponseEntity.ok(invoiceRecordService.findByDocumentId(documentId));
    }

    @GetMapping("/by-category/{categoryId}")
    public ResponseEntity<List<InvoiceRecordResponse>> listByCategory(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(invoiceRecordService.listByCategory(categoryId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        invoiceRecordService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
