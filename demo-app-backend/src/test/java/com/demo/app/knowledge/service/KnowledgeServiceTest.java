package com.demo.app.knowledge.service;

import com.demo.app.content.service.DocumentService;
import com.demo.app.knowledge.dto.KnowledgeIngestRequest;
import com.demo.app.knowledge.entity.KnowledgeEntity;
import com.demo.app.knowledge.entity.KnowledgeEntitySource;
import com.demo.app.knowledge.entity.KnowledgeEntitySourceId;
import com.demo.app.knowledge.entity.KnowledgeRelationship;
import com.demo.app.knowledge.repository.KnowledgeEntityRepository;
import com.demo.app.knowledge.repository.KnowledgeEntitySourceRepository;
import com.demo.app.knowledge.repository.KnowledgeRelationshipRepository;
import com.demo.app.platform.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock KnowledgeEntityRepository entityRepo;
    @Mock KnowledgeRelationshipRepository relationshipRepo;
    @Mock KnowledgeEntitySourceRepository entitySourceRepo;
    @Mock DocumentService documentService;

    @InjectMocks KnowledgeService knowledgeService;

    private final UUID DOC_ID = UUID.randomUUID();
    private final UUID CAT_ID = UUID.randomUUID();
    private final UUID ENTITY_ID = UUID.randomUUID();

    // ─── ingest ───────────────────────────────────────────────────────────

    @Test
    void ingest_rejected_marksFailed_andReturnsEarly() {
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "REJECTED",
                List.of("file too large"), null, null, null, null, null, null);

        knowledgeService.ingest(request);

        verify(documentService).updateExtractionStatus(DOC_ID, "FAILED", "REJECTED", "file too large");
        verify(entityRepo, never()).save(any());
    }

    @Test
    void ingest_error_marksFailed_withStatusAsWarning_whenNoWarnings() {
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "ERROR",
                null, null, null, null, null, null, null);

        knowledgeService.ingest(request);

        verify(documentService).updateExtractionStatus(DOC_ID, "FAILED", "ERROR", "ERROR");
    }

    @Test
    void ingest_pass_savesTechnologiesAndCompletesDocument() {
        var tech = new KnowledgeIngestRequest.TechEntityDto("Spring Boot", "3.3", "Framework",
                List.of("Spring", "SB"));
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                "Guide", "A guide", "tutorial", List.of(tech), null, null);

        var savedEntity = buildEntity("Technology", "Spring Boot");
        when(entityRepo.save(any())).thenReturn(savedEntity);
        when(entitySourceRepo.existsById(any())).thenReturn(false);

        knowledgeService.ingest(request);

        verify(entityRepo).save(any());
        verify(entitySourceRepo).save(any());
        verify(documentService).updateExtractionStatus(DOC_ID, "COMPLETED");
    }

    @Test
    void ingest_skipsBlankTechnologyName() {
        var tech = new KnowledgeIngestRequest.TechEntityDto("", null, null, null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, List.of(tech), null, null);

        knowledgeService.ingest(request);

        verify(entityRepo, never()).save(any());
        verify(documentService).updateExtractionStatus(DOC_ID, "COMPLETED");
    }

    @Test
    void ingest_savesConcepts() {
        var concept = new KnowledgeIngestRequest.ConceptEntityDto("DDD", "Domain-Driven Design", List.of("Domain model"));
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, null, List.of(concept), null);

        var savedEntity = buildEntity("Concept", "DDD");
        when(entityRepo.save(any())).thenReturn(savedEntity);
        when(entitySourceRepo.existsById(any())).thenReturn(false);

        knowledgeService.ingest(request);

        verify(entityRepo).save(argThat(e -> "Concept".equals(e.getEntityType())));
        verify(documentService).updateExtractionStatus(DOC_ID, "COMPLETED");
    }

    @Test
    void ingest_savesRelationship_whenBothEntitiesResolved() {
        var tech1 = new KnowledgeIngestRequest.TechEntityDto("Spring Boot", null, null, null);
        var tech2 = new KnowledgeIngestRequest.TechEntityDto("Tomcat", null, null, null);
        var rel = new KnowledgeIngestRequest.RelationshipDto("Spring Boot", "Tomcat", "USES", 0.9);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, List.of(tech1, tech2), null, List.of(rel));

        var entity1 = buildEntity("Technology", "Spring Boot");
        var entity2 = buildEntity("Technology", "Tomcat");
        when(entityRepo.save(any()))
                .thenReturn(entity1)
                .thenReturn(entity2);
        when(entitySourceRepo.existsById(any())).thenReturn(false);

        knowledgeService.ingest(request);

        verify(relationshipRepo).save(argThat(r ->
                r.getRelationType().equals("USES") && r.getWeight() == 0.9));
    }

    @Test
    void ingest_skipsRelationship_whenSourceMissing() {
        var rel = new KnowledgeIngestRequest.RelationshipDto(null, "Tomcat", "USES", null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, null, null, List.of(rel));

        knowledgeService.ingest(request);

        verify(relationshipRepo, never()).save(any());
    }

    @Test
    void ingest_skipsRelationship_whenSelfLoop() {
        var tech = new KnowledgeIngestRequest.TechEntityDto("Spring", null, null, null);
        var rel = new KnowledgeIngestRequest.RelationshipDto("Spring", "Spring", "USES", null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, List.of(tech), null, List.of(rel));

        var entity = buildEntity("Technology", "Spring");
        when(entityRepo.save(any())).thenReturn(entity);
        when(entitySourceRepo.existsById(any())).thenReturn(false);

        knowledgeService.ingest(request);

        verify(relationshipRepo, never()).save(any());
    }

    @Test
    void ingest_usesDefaultRelationType_whenNull() {
        var tech1 = new KnowledgeIngestRequest.TechEntityDto("A", null, null, null);
        var tech2 = new KnowledgeIngestRequest.TechEntityDto("B", null, null, null);
        var rel = new KnowledgeIngestRequest.RelationshipDto("A", "B", null, null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, List.of(tech1, tech2), null, List.of(rel));

        var e1 = buildEntity("Technology", "A");
        var e2 = buildEntity("Technology", "B");
        when(entityRepo.save(any())).thenReturn(e1).thenReturn(e2);
        when(entitySourceRepo.existsById(any())).thenReturn(false);

        knowledgeService.ingest(request);

        verify(relationshipRepo).save(argThat(r -> "RELATED_TO".equals(r.getRelationType())));
    }

    @Test
    void ingest_skipsSourceSave_whenAlreadyExists() {
        var tech = new KnowledgeIngestRequest.TechEntityDto("Spring Boot", null, null, null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, List.of(tech), null, null);

        var savedEntity = buildEntity("Technology", "Spring Boot");
        when(entityRepo.save(any())).thenReturn(savedEntity);
        when(entitySourceRepo.existsById(any())).thenReturn(true);

        knowledgeService.ingest(request);

        verify(entitySourceRepo, never()).save(any());
    }

    // ─── search ───────────────────────────────────────────────────────────

    @Test
    void search_noParams_returnsAll() {
        var pageable = PageRequest.of(0, 10);
        var page = new PageImpl<>(List.of(buildEntity("Technology", "Spring")));
        when(entityRepo.findAll(pageable)).thenReturn(page);

        var result = knowledgeService.search(null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void search_typeOnly_filtersByType() {
        var pageable = PageRequest.of(0, 10);
        var results = List.of(buildEntity("Concept", "DDD"));
        when(entityRepo.findByEntityType("Concept", pageable)).thenReturn(results);

        var result = knowledgeService.search(null, "Concept", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).entityType()).isEqualTo("Concept");
    }

    @Test
    void search_withQuery_usesTrigramSearch() {
        var pageable = PageRequest.of(0, 10);
        var entity = buildEntity("Technology", "Spring Boot");
        when(entityRepo.searchByName(eq("spring"), eq("%spring%"), anyInt()))
                .thenReturn(List.of(entity));

        var result = knowledgeService.search("spring", null, pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void search_withQueryAndType_filtersAfterTrigram() {
        var pageable = PageRequest.of(0, 10);
        var tech = buildEntity("Technology", "Spring");
        var concept = buildEntity("Concept", "Spring Pattern");
        when(entityRepo.searchByName(any(), any(), anyInt())).thenReturn(List.of(tech, concept));

        var result = knowledgeService.search("spring", "Technology", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).entityType()).isEqualTo("Technology");
    }

    @Test
    void search_emptyQuery_returnsAll() {
        var pageable = PageRequest.of(0, 10);
        var page = new PageImpl<>(List.of(buildEntity("Technology", "Spring")));
        when(entityRepo.findAll(pageable)).thenReturn(page);

        var result = knowledgeService.search("  ", null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ─── findById ─────────────────────────────────────────────────────────

    @Test
    void findById_returnsEntityWithRelationshipsAndSources() {
        var entity = buildEntity("Technology", "Spring Boot");
        var otherId = UUID.randomUUID();
        var rel = KnowledgeRelationship.builder()
                .id(UUID.randomUUID())
                .sourceEntityId(entity.getId())
                .targetEntityId(otherId)
                .relationType("USES")
                .weight(1.0)
                .build();
        var sourceId = new KnowledgeEntitySourceId(entity.getId(), DOC_ID);
        var source = KnowledgeEntitySource.builder().id(sourceId).excerpt("excerpt").build();

        when(entityRepo.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(relationshipRepo.findBySourceEntityIdOrTargetEntityId(entity.getId(), entity.getId()))
                .thenReturn(List.of(rel));
        when(entitySourceRepo.findByIdEntityId(entity.getId())).thenReturn(List.of(source));
        when(entityRepo.findById(otherId)).thenReturn(Optional.empty());

        var result = knowledgeService.findById(entity.getId());

        assertThat(result.name()).isEqualTo("Spring Boot");
        assertThat(result.relationships()).hasSize(1);
        assertThat(result.relationships().get(0).direction()).isEqualTo("OUTGOING");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).excerpt()).isEqualTo("excerpt");
    }

    @Test
    void findById_incomingRelationship_labeledCorrectly() {
        var entity = buildEntity("Technology", "Tomcat");
        var sourceId = UUID.randomUUID();
        var rel = KnowledgeRelationship.builder()
                .id(UUID.randomUUID())
                .sourceEntityId(sourceId)
                .targetEntityId(entity.getId())
                .relationType("USES")
                .weight(1.0)
                .build();

        when(entityRepo.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(relationshipRepo.findBySourceEntityIdOrTargetEntityId(entity.getId(), entity.getId()))
                .thenReturn(List.of(rel));
        when(entitySourceRepo.findByIdEntityId(entity.getId())).thenReturn(List.of());
        when(entityRepo.findById(sourceId)).thenReturn(Optional.empty());

        var result = knowledgeService.findById(entity.getId());

        assertThat(result.relationships().get(0).direction()).isEqualTo("INCOMING");
    }

    @Test
    void findById_throws_whenNotFound() {
        when(entityRepo.findById(ENTITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> knowledgeService.findById(ENTITY_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── getGraph ─────────────────────────────────────────────────────────

    @Test
    void getGraph_returnsNodesAndEdges() {
        var center = buildEntity("Technology", "Spring Boot");
        var neighbor = buildEntity("Technology", "Tomcat");
        var rel = KnowledgeRelationship.builder()
                .id(UUID.randomUUID())
                .sourceEntityId(center.getId())
                .targetEntityId(neighbor.getId())
                .relationType("USES")
                .weight(1.0)
                .build();

        when(entityRepo.findById(center.getId())).thenReturn(Optional.of(center));
        when(relationshipRepo.findBySourceEntityIdOrTargetEntityId(center.getId(), center.getId()))
                .thenReturn(List.of(rel));
        when(entityRepo.findAllById(any())).thenReturn(List.of(neighbor));

        var result = knowledgeService.getGraph(center.getId());

        assertThat(result.nodes()).hasSize(2);
        assertThat(result.edges()).hasSize(1);
        assertThat(result.edges().get(0).relationType()).isEqualTo("USES");
    }

    @Test
    void getGraph_throws_whenEntityNotFound() {
        when(entityRepo.findById(ENTITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> knowledgeService.getGraph(ENTITY_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getGraph_centerOnly_whenNoNeighbors() {
        var center = buildEntity("Technology", "Spring Boot");

        when(entityRepo.findById(center.getId())).thenReturn(Optional.of(center));
        when(relationshipRepo.findBySourceEntityIdOrTargetEntityId(center.getId(), center.getId()))
                .thenReturn(List.of());
        when(entityRepo.findAllById(any())).thenReturn(List.of());

        var result = knowledgeService.getGraph(center.getId());

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.edges()).isEmpty();
    }

    // ─── additional branch coverage ───────────────────────────────────────

    @Test
    void ingest_tech_nullName_isSkipped() {
        var tech = new KnowledgeIngestRequest.TechEntityDto(null, "1.0", "Framework", null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, List.of(tech), null, null);

        knowledgeService.ingest(request);

        verify(entityRepo, never()).save(any());
        verify(documentService).updateExtractionStatus(DOC_ID, "COMPLETED");
    }

    @Test
    void ingest_tech_nullVersionAndCategory_hasNullProperties() {
        var tech = new KnowledgeIngestRequest.TechEntityDto("Kafka", null, null, null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, List.of(tech), null, null);

        var savedEntity = buildEntity("Technology", "Kafka");
        when(entityRepo.save(any())).thenReturn(savedEntity);
        when(entitySourceRepo.existsById(any())).thenReturn(false);

        knowledgeService.ingest(request);

        verify(entityRepo).save(argThat(e -> e.getProperties() == null));
    }

    @Test
    void ingest_concept_nullName_isSkipped() {
        var concept = new KnowledgeIngestRequest.ConceptEntityDto(null, "some def", null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, null, List.of(concept), null);

        knowledgeService.ingest(request);

        verify(entityRepo, never()).save(any());
    }

    @Test
    void ingest_concept_nullDefinition_hasNullProperties() {
        var concept = new KnowledgeIngestRequest.ConceptEntityDto("CQRS", null, null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, null, List.of(concept), null);

        var savedEntity = buildEntity("Concept", "CQRS");
        when(entityRepo.save(any())).thenReturn(savedEntity);
        when(entitySourceRepo.existsById(any())).thenReturn(false);

        knowledgeService.ingest(request);

        verify(entityRepo).save(argThat(e -> e.getProperties() == null));
    }

    @Test
    void ingest_relationship_nullTarget_isSkipped() {
        var rel = new KnowledgeIngestRequest.RelationshipDto("Spring", null, "USES", null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, null, null, List.of(rel));

        knowledgeService.ingest(request);

        verify(relationshipRepo, never()).save(any());
    }

    @Test
    void ingest_relationship_resolveEntityViaDb_whenNotInMap() {
        // Source is not in the in-memory map — fallback to DB lookup
        var tech = new KnowledgeIngestRequest.TechEntityDto("Tomcat", null, null, null);
        var rel = new KnowledgeIngestRequest.RelationshipDto("SpringBoot", "Tomcat", "USES", null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, List.of(tech), null, List.of(rel));

        var tomcat = buildEntity("Technology", "Tomcat");
        var springBoot = buildEntity("Technology", "SpringBoot");
        when(entityRepo.save(any())).thenReturn(tomcat);
        when(entitySourceRepo.existsById(any())).thenReturn(false);
        // DB lookup for "SpringBoot" (not in map)
        when(entityRepo.findByDocumentId(DOC_ID)).thenReturn(List.of(springBoot));

        knowledgeService.ingest(request);

        verify(relationshipRepo).save(any());
    }

    @Test
    void ingest_relationship_sourceNotFoundAnywhere_isSkipped() {
        var tech = new KnowledgeIngestRequest.TechEntityDto("Tomcat", null, null, null);
        var rel = new KnowledgeIngestRequest.RelationshipDto("Unknown", "Tomcat", "USES", null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, List.of(tech), null, List.of(rel));

        var tomcat = buildEntity("Technology", "Tomcat");
        when(entityRepo.save(any())).thenReturn(tomcat);
        when(entitySourceRepo.existsById(any())).thenReturn(false);
        when(entityRepo.findByDocumentId(DOC_ID)).thenReturn(List.of());

        knowledgeService.ingest(request);

        verify(relationshipRepo, never()).save(any());
    }

    @Test
    void ingest_concept_blankName_isSkipped() {
        var concept = new KnowledgeIngestRequest.ConceptEntityDto("   ", "some def", null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, null, List.of(concept), null);

        knowledgeService.ingest(request);

        verify(entityRepo, never()).save(any());
    }

    @Test
    void ingest_relationship_targetNotFoundAnywhere_isSkipped() {
        // Source is found (in map), target cannot be resolved
        var tech = new KnowledgeIngestRequest.TechEntityDto("Spring", null, null, null);
        var rel = new KnowledgeIngestRequest.RelationshipDto("Spring", "UnknownTarget", "USES", null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, List.of(tech), null, List.of(rel));

        var spring = buildEntity("Technology", "Spring");
        when(entityRepo.save(any())).thenReturn(spring);
        when(entitySourceRepo.existsById(any())).thenReturn(false);
        when(entityRepo.findByDocumentId(DOC_ID)).thenReturn(List.of());

        knowledgeService.ingest(request);

        verify(relationshipRepo, never()).save(any());
    }

    @Test
    void ingest_tech_emptyAliases_storedAsNull() {
        // toArray with empty list returns null → stored as null aliases
        var tech = new KnowledgeIngestRequest.TechEntityDto("Redis", "7.0", null, List.of());
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, List.of(tech), null, null);

        var savedEntity = buildEntity("Technology", "Redis");
        when(entityRepo.save(any())).thenReturn(savedEntity);
        when(entitySourceRepo.existsById(any())).thenReturn(false);

        knowledgeService.ingest(request);

        verify(entityRepo).save(argThat(e -> e.getAliases() == null));
    }

    @Test
    void search_blankType_treatedAsAbsent_returnsAll() {
        // covers: entityType != null && !entityType.isBlank() where isBlank()=true → hasType=false
        var pageable = PageRequest.of(0, 10);
        var page = new PageImpl<>(List.of(buildEntity("Technology", "Spring")));
        when(entityRepo.findAll(pageable)).thenReturn(page);

        var result = knowledgeService.search(null, "   ", pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void search_withQuery_beyondLastPage_returnsEmpty() {
        // page 5 but only 3 results — start >= results.size()
        var pageable = PageRequest.of(5, 10);
        var entity = buildEntity("Technology", "Spring");
        when(entityRepo.searchByName(any(), any(), anyInt())).thenReturn(List.of(entity, entity, entity));

        var result = knowledgeService.search("spring", null, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    void search_returnsEntityWithAliases_inSummary() {
        // covers toSummary: aliases != null branch
        var pageable = PageRequest.of(0, 10);
        var entity = buildEntityWithAliases("Technology", "Postgres", new String[]{"PG", "psql"});
        var page = new org.springframework.data.domain.PageImpl<>(List.of(entity));
        when(entityRepo.findAll(pageable)).thenReturn(page);

        var result = knowledgeService.search(null, null, pageable);

        assertThat(result.getContent().get(0).aliases()).containsExactly("PG", "psql");
    }

    @Test
    void findById_entityWithAliases_includesThemInResponse() {
        // covers toResponse: aliases != null branch
        var entity = buildEntityWithAliases("Technology", "Postgres", new String[]{"PG", "psql"});
        when(entityRepo.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(relationshipRepo.findBySourceEntityIdOrTargetEntityId(entity.getId(), entity.getId()))
                .thenReturn(List.of());
        when(entitySourceRepo.findByIdEntityId(entity.getId())).thenReturn(List.of());

        var result = knowledgeService.findById(entity.getId());

        assertThat(result.aliases()).containsExactly("PG", "psql");
    }

    @Test
    void ingest_relationship_resolveEntityViaDb_nonMatchingEntityIgnored() {
        // covers resolveEntity filter: DB returns entity but name doesn't match → returns null
        var tech = new KnowledgeIngestRequest.TechEntityDto("Tomcat", null, null, null);
        var rel = new KnowledgeIngestRequest.RelationshipDto("SpringBoot", "Tomcat", "USES", null);
        var request = new KnowledgeIngestRequest(DOC_ID, CAT_ID, "PASS", null,
                null, null, null, List.of(tech), null, List.of(rel));

        var tomcat = buildEntity("Technology", "Tomcat");
        var differentEntity = buildEntity("Technology", "React");  // name doesn't match "SpringBoot"
        when(entityRepo.save(any())).thenReturn(tomcat);
        when(entitySourceRepo.existsById(any())).thenReturn(false);
        // DB returns an entity whose name does NOT match "SpringBoot" → filter returns false → source == null
        when(entityRepo.findByDocumentId(DOC_ID)).thenReturn(List.of(differentEntity));

        knowledgeService.ingest(request);

        // source resolved to null → relationship skipped
        verify(relationshipRepo, never()).save(any());
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private KnowledgeEntity buildEntity(String type, String name) {
        return KnowledgeEntity.builder()
                .id(UUID.randomUUID())
                .documentId(DOC_ID)
                .entityType(type)
                .name(name)
                .createdAt(Instant.now())
                .build();
    }

    private KnowledgeEntity buildEntityWithAliases(String type, String name, String[] aliases) {
        return KnowledgeEntity.builder()
                .id(UUID.randomUUID())
                .documentId(DOC_ID)
                .entityType(type)
                .name(name)
                .aliases(aliases)
                .createdAt(Instant.now())
                .build();
    }
}
