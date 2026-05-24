package com.demo.app.knowledge.service;

import com.demo.app.content.service.DocumentService;
import com.demo.app.knowledge.dto.*;
import com.demo.app.knowledge.entity.KnowledgeEntity;
import com.demo.app.knowledge.entity.KnowledgeEntitySource;
import com.demo.app.knowledge.entity.KnowledgeEntitySourceId;
import com.demo.app.knowledge.entity.KnowledgeRelationship;
import com.demo.app.knowledge.repository.KnowledgeEntityRepository;
import com.demo.app.knowledge.repository.KnowledgeEntitySourceRepository;
import com.demo.app.knowledge.repository.KnowledgeRelationshipRepository;
import com.demo.app.platform.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional
public class KnowledgeService {

    private final KnowledgeEntityRepository entityRepo;
    private final KnowledgeRelationshipRepository relationshipRepo;
    private final KnowledgeEntitySourceRepository entitySourceRepo;
    private final DocumentService documentService;

    public void ingest(KnowledgeIngestRequest request) {
        String status = request.extractionStatus();
        if ("REJECTED".equals(status) || "ERROR".equals(status)) {
            String warnings = request.guardrailWarnings() != null
                    ? String.join("; ", request.guardrailWarnings()) : status;
            documentService.updateExtractionStatus(request.documentId(), "FAILED", status, warnings);
            return;
        }

        Map<String, KnowledgeEntity> entityByName = new HashMap<>();

        if (request.technologies() != null) {
            for (var tech : request.technologies()) {
                if (tech.name() == null || tech.name().isBlank()) continue;
                Map<String, Object> props = new HashMap<>();
                if (tech.version() != null) props.put("version", tech.version());
                if (tech.category() != null) props.put("category", tech.category());
                var entity = KnowledgeEntity.builder()
                        .documentId(request.documentId())
                        .entityType("Technology")
                        .name(tech.name())
                        .aliases(toArray(tech.aliases()))
                        .properties(props.isEmpty() ? null : props)
                        .build();
                entity = entityRepo.save(entity);
                entityByName.put(tech.name(), entity);
                saveSource(entity.getId(), request.documentId());
            }
        }

        if (request.concepts() != null) {
            for (var concept : request.concepts()) {
                if (concept.name() == null || concept.name().isBlank()) continue;
                Map<String, Object> props = new HashMap<>();
                if (concept.definition() != null) props.put("definition", concept.definition());
                var entity = KnowledgeEntity.builder()
                        .documentId(request.documentId())
                        .entityType("Concept")
                        .name(concept.name())
                        .aliases(toArray(concept.relatedConcepts()))
                        .properties(props.isEmpty() ? null : props)
                        .build();
                entity = entityRepo.save(entity);
                entityByName.put(concept.name(), entity);
                saveSource(entity.getId(), request.documentId());
            }
        }

        if (request.relationships() != null) {
            for (var rel : request.relationships()) {
                if (rel.source() == null || rel.target() == null) continue;
                var source = resolveEntity(rel.source(), entityByName, request.documentId());
                var target = resolveEntity(rel.target(), entityByName, request.documentId());
                if (source == null || target == null || source.getId().equals(target.getId())) continue;
                var relationship = KnowledgeRelationship.builder()
                        .sourceEntityId(source.getId())
                        .targetEntityId(target.getId())
                        .relationType(rel.relationType() != null ? rel.relationType() : "RELATED_TO")
                        .weight(rel.weight() != null ? rel.weight() : 1.0)
                        .build();
                relationshipRepo.save(relationship);
            }
        }

        documentService.updateExtractionStatus(request.documentId(), "COMPLETED");
    }

    @Transactional(readOnly = true)
    public Page<KnowledgeEntitySummary> search(String q, String entityType, Pageable pageable) {
        boolean hasQ = q != null && !q.isBlank();
        boolean hasType = entityType != null && !entityType.isBlank();

        if (!hasQ && !hasType) {
            return entityRepo.findAll(pageable).map(this::toSummary);
        }

        if (!hasQ) {
            var results = entityRepo.findByEntityType(entityType, pageable);
            long total = results.size();
            return new PageImpl<>(results.stream().map(this::toSummary).toList(), pageable, total);
        }

        int limit = pageable.getPageSize() * (pageable.getPageNumber() + 1);
        var results = entityRepo.searchByName(q, "%" + q + "%", limit);
        if (hasType) {
            results = results.stream()
                    .filter(e -> entityType.equals(e.getEntityType()))
                    .toList();
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), results.size());
        var page = (start >= results.size()) ? List.<KnowledgeEntity>of() : results.subList(start, end);
        return new PageImpl<>(page.stream().map(this::toSummary).toList(), pageable, results.size());
    }

    @Transactional(readOnly = true)
    public KnowledgeEntityResponse findById(UUID id) {
        var entity = entityRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeEntity", id));
        var allRelationships = relationshipRepo.findBySourceEntityIdOrTargetEntityId(id, id);
        var sources = entitySourceRepo.findByIdEntityId(id);
        return toResponse(entity, allRelationships, sources);
    }

    @Transactional(readOnly = true)
    public KnowledgeGraphResponse getGraph(UUID entityId) {
        var center = entityRepo.findById(entityId)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeEntity", entityId));
        var edges = relationshipRepo.findBySourceEntityIdOrTargetEntityId(entityId, entityId);
        var neighborIds = edges.stream()
                .flatMap(r -> Stream.of(r.getSourceEntityId(), r.getTargetEntityId()))
                .filter(nid -> !nid.equals(entityId))
                .collect(Collectors.toSet());
        var allNodes = new ArrayList<KnowledgeEntity>();
        allNodes.add(center);
        allNodes.addAll(entityRepo.findAllById(neighborIds));
        return new KnowledgeGraphResponse(toNodeDtos(allNodes), toEdgeDtos(edges));
    }

    private void saveSource(UUID entityId, UUID documentId) {
        var sourceId = new KnowledgeEntitySourceId(entityId, documentId);
        if (!entitySourceRepo.existsById(sourceId)) {
            entitySourceRepo.save(KnowledgeEntitySource.builder()
                    .id(sourceId)
                    .build());
        }
    }

    private KnowledgeEntity resolveEntity(String name, Map<String, KnowledgeEntity> entityByName, UUID documentId) {
        if (entityByName.containsKey(name)) {
            return entityByName.get(name);
        }
        var existing = entityRepo.findByDocumentId(documentId).stream()
                .filter(e -> name.equalsIgnoreCase(e.getName()))
                .findFirst();
        return existing.orElse(null);
    }

    private String[] toArray(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return list.toArray(new String[0]);
    }

    private KnowledgeEntitySummary toSummary(KnowledgeEntity e) {
        return new KnowledgeEntitySummary(
                e.getId(), e.getDocumentId(), e.getEntityType(), e.getName(),
                e.getAliases() != null ? Arrays.asList(e.getAliases()) : List.of(),
                e.getCreatedAt());
    }

    private KnowledgeEntityResponse toResponse(KnowledgeEntity entity,
                                               List<KnowledgeRelationship> relationships,
                                               List<KnowledgeEntitySource> sources) {
        var relSummaries = relationships.stream().map(r -> {
            boolean outgoing = entity.getId().equals(r.getSourceEntityId());
            UUID otherId = outgoing ? r.getTargetEntityId() : r.getSourceEntityId();
            String otherName = entityRepo.findById(otherId).map(KnowledgeEntity::getName).orElse("Unknown");
            return new KnowledgeEntityResponse.RelationshipSummary(
                    r.getId(), otherId, otherName,
                    outgoing ? "OUTGOING" : "INCOMING",
                    r.getRelationType(), r.getWeight());
        }).toList();

        var sourceSummaries = sources.stream().map(s ->
                new KnowledgeEntityResponse.SourceSummary(
                        s.getId().getDocumentId(), s.getExcerpt(), s.getPageNumber())
        ).toList();

        return new KnowledgeEntityResponse(
                entity.getId(), entity.getDocumentId(), entity.getEntityType(), entity.getName(),
                entity.getAliases() != null ? Arrays.asList(entity.getAliases()) : List.of(),
                entity.getProperties(), entity.getCreatedAt(),
                relSummaries, sourceSummaries);
    }

    private List<KnowledgeGraphResponse.NodeDto> toNodeDtos(List<KnowledgeEntity> nodes) {
        return nodes.stream().map(n ->
                new KnowledgeGraphResponse.NodeDto(n.getId(), n.getName(), n.getEntityType(), n.getProperties())
        ).toList();
    }

    private List<KnowledgeGraphResponse.EdgeDto> toEdgeDtos(List<KnowledgeRelationship> edges) {
        return edges.stream().map(e ->
                new KnowledgeGraphResponse.EdgeDto(e.getId(), e.getSourceEntityId(), e.getTargetEntityId(),
                        e.getRelationType(), e.getWeight())
        ).toList();
    }
}
