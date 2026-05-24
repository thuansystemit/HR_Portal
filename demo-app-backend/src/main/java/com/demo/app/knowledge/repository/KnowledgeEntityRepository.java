package com.demo.app.knowledge.repository;

import com.demo.app.knowledge.entity.KnowledgeEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface KnowledgeEntityRepository extends JpaRepository<KnowledgeEntity, UUID> {

    List<KnowledgeEntity> findByDocumentId(UUID documentId);

    List<KnowledgeEntity> findByEntityType(String entityType, Pageable pageable);

    @Query(value = "SELECT * FROM knowledge_entities WHERE similarity(name, :q) > 0.3 OR name ILIKE :qLike ORDER BY similarity(name, :q) DESC LIMIT :limit",
            nativeQuery = true)
    List<KnowledgeEntity> searchByName(@Param("q") String q, @Param("qLike") String qLike, @Param("limit") int limit);
}
