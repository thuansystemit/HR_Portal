package com.demo.app.content.repository;

import com.demo.app.content.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByCategoryIdAndDeletedAtIsNullAndUploadStatus(
            UUID categoryId, String uploadStatus, Pageable pageable);

    Optional<Document> findByIdAndDeletedAtIsNull(UUID id);
}
