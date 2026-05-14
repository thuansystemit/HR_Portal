package com.demo.app.content.repository;

import com.demo.app.content.entity.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, UUID> {

    @Query("SELECT dc FROM DocumentCategory dc WHERE dc.deletedAt IS NULL ORDER BY dc.name")
    List<DocumentCategory> findAllActive();

    Optional<DocumentCategory> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);
}
