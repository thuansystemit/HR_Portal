package com.demo.app.knowledge.repository;

import com.demo.app.knowledge.entity.KnowledgeRelationship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KnowledgeRelationshipRepository extends JpaRepository<KnowledgeRelationship, UUID> {

    List<KnowledgeRelationship> findBySourceEntityId(UUID sourceEntityId);

    List<KnowledgeRelationship> findByTargetEntityId(UUID targetEntityId);

    List<KnowledgeRelationship> findBySourceEntityIdOrTargetEntityId(UUID sourceEntityId, UUID targetEntityId);
}
