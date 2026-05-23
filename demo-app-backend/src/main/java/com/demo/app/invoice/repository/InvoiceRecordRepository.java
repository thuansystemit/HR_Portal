package com.demo.app.invoice.repository;

import com.demo.app.invoice.entity.InvoiceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRecordRepository extends JpaRepository<InvoiceRecord, UUID> {

    boolean existsByDocumentId(UUID documentId);

    Optional<InvoiceRecord> findByDocumentId(UUID documentId);

    List<InvoiceRecord> findByDocumentCategoryId(UUID documentCategoryId);
}
