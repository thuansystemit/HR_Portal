package com.demo.app.knowledge.repository;

import com.demo.app.knowledge.entity.KnowledgeEntitySource;
import com.demo.app.knowledge.entity.KnowledgeEntitySourceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KnowledgeEntitySourceRepository extends JpaRepository<KnowledgeEntitySource, KnowledgeEntitySourceId> {

    List<KnowledgeEntitySource> findByIdEntityId(UUID entityId);

    List<KnowledgeEntitySource> findByIdDocumentId(UUID documentId);
}
