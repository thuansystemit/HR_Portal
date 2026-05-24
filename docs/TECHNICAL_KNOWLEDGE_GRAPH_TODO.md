# TECHNICAL Document Type — Knowledge Graph Implementation TODO

Architecture recommendation: **PostgreSQL + pgvector** (no new infrastructure).
Extraction: extend existing Python cv-batch-extractor as a second pipeline.
Query modes: RAG (natural language), faceted entity search, graph traversal.

> Last updated: 2026-05-24
> Status key: ✅ done · 🔲 not started · ⚠️ partial

---

## Phase 1 — Foundation & Text Search ✅ COMPLETE

### 1.1 Database ✅

- [x] Flyway V14: extend `dc_document_type_chk` CHECK constraint to include `'TECHNICAL'`
- [x] Flyway V14: install `pg_trgm` extension
- [x] Flyway V14: create `knowledge_entities` table
  ```sql
  id UUID PK, document_id UUID FK, entity_type VARCHAR CHECK(...),
  name VARCHAR, aliases TEXT[], properties JSONB, created_at TIMESTAMPTZ
  ```
- [x] Flyway V14: create `knowledge_relationships` table
  ```sql
  id UUID PK, source_entity_id UUID FK, target_entity_id UUID FK,
  relation_type VARCHAR, weight FLOAT,
  UNIQUE(source_entity_id, target_entity_id, relation_type),
  CHECK(source_entity_id <> target_entity_id)
  ```
- [x] Flyway V14: create `knowledge_entity_sources` table
  ```sql
  entity_id UUID FK, document_id UUID FK (composite PK), excerpt TEXT, page_number INT
  ```
- [x] GIN index on `knowledge_entities.aliases` (array contains lookup)
- [x] Trigram GIN index on `knowledge_entities.name` (fuzzy name search)
- [x] B-tree index on `knowledge_entities.entity_type` (type filter)
- [x] GIN index on `knowledge_entities.properties` (JSONB)

> **Note:** `docker-compose.yml` updated from `postgres:16-alpine` to `pgvector/pgvector:pg16`
> to satisfy the V15 pgvector migration requirement (fixed at Docker rebuild time).

### 1.2 Backend — `com.demo.app.knowledge` module ✅

- [x] `KnowledgeEntity` JPA entity (`@JdbcTypeCode` for `text[]` aliases and `jsonb` properties)
- [x] `KnowledgeRelationship` JPA entity
- [x] `KnowledgeEntitySource` JPA entity + `KnowledgeEntitySourceId` embeddable composite key
- [x] `KnowledgeEntityRepository` — `findByDocumentId`, `findByEntityType(Pageable)`, trigram `searchByName` native query
- [x] `KnowledgeRelationshipRepository`
- [x] `KnowledgeEntitySourceRepository`
- [x] `KnowledgeIngestRequest` DTO — `TechEntityDto`, `ConceptEntityDto`, `RelationshipDto` nested records
- [x] `KnowledgeEntitySummary` + `KnowledgeEntityResponse` + `KnowledgeGraphResponse` DTOs
- [x] `KnowledgeService`
  - `ingest()` — handles REJECTED/ERROR → FAILED, saves technologies + concepts + relationships, calls `documentService.updateExtractionStatus`
  - `search(q, entityType, Pageable)` — trigram search + type filter + pagination
  - `findById(UUID)` — entity + outgoing/incoming relationships + sources
  - `getGraph(UUID)` — depth-1 neighbourhood nodes + edges
- [x] `KnowledgeIngestController` — `POST /api/v1/knowledge/ingest` (`@PreAuthorize("hasRole('SERVICE')")`)
- [x] `KnowledgeQueryController`
  - `GET /api/v1/knowledge/entities?q=&type=&page=&size=`
  - `GET /api/v1/knowledge/entities/{id}`
  - `GET /api/v1/knowledge/entities/{id}/graph`
- [x] `SecurityConfig` — `.requestMatchers(POST, "/api/v1/knowledge/ingest").hasRole("SERVICE")`
- [x] `KnowledgeServiceTest` — 32 unit tests, ≥95% line + branch coverage (JaCoCo threshold met)

### 1.3 Backend — content module changes ⚠️

- [x] `DocumentType` enum: added `TECHNICAL` value (fixes Jackson deserialization for category create/update)
- [ ] `DocumentService` — explicit TECHNICAL categorization hook (currently falls through to generic path; no special routing logic needed yet)
- [ ] TECHNICAL documents: `extraction_status = PENDING` on upload emitted automatically via existing upload flow (same behaviour as CV — no code change needed, works by convention)

### 1.4 Python extractor — technical pipeline ✅

- [x] `domain/technical_schema.py` — `KnowledgeExtraction`, `TechEntity`, `ConceptEntity`, `Relationship` Pydantic models
- [x] `llm/prompt_templates/technical_extraction.py` — structured extraction prompt with JSON schema instructions for technologies, concepts, relationships
- [x] `workflow/extraction_pipeline.py` — `_run_stages_technical()` branch: file validation → OCR → text validation → chunk → LLM (technical prompt) → `KnowledgeExtraction.model_validate` → `ctx.knowledge_data`
- [x] `ingestion/file_watcher/watcher.py` — routes `technical/` upload paths; passes `document_type="TECHNICAL"` to `WorkerPool.submit()`
- [x] `workflow/orchestration.py` — `_notify_knowledge_backend()` posts to `POST /api/v1/knowledge/ingest` with full `KnowledgeExtraction` payload; branches from `_notify_cv_backend()` on `document_type`
- [x] `domain/models.py` — `PipelineContext.knowledge_data` + `ProcessingResult.knowledge_data` fields added
- [x] Output file: `tech_{title_slug}_{short_id}.json` written to `output_dir` (local audit copy)
- [x] Docker image rebuilt and redeployed with all changes

### 1.5 Frontend — `src/app/features/knowledge` module ✅

- [x] `features/knowledge/models/knowledge.model.ts` — `KnowledgeEntitySummary`, `KnowledgeEntity`, `RelationshipSummary`, `SourceSummary`, `KnowledgePage`, `EntityType`
- [x] `features/knowledge/services/knowledge.api.ts` — `search()`, `getById()` Angular HttpClient service
- [x] `features/knowledge/store/knowledge.store.ts` — signal-based store (6 private signals, `search()` + `loadById()` methods)
- [x] Route `/knowledge` — `EntityListPage`: search input + entity type filter dropdown, paginated table, type badge cell template
- [x] Route `/knowledge/:entityId` — `EntityDetailPage`: Info card (name/type/aliases/properties), Relationships card (direction badge/linked entity/weight), Sources card (document/excerpt/page)
- [x] `knowledge.routes.ts` — lazy-loaded child routes
- [x] `app.routes.ts` — `/knowledge` route registered
- [x] Sidebar nav — `{ label: 'Knowledge Base', icon: 'bi-diagram-3', route: '/knowledge' }` added
- [x] `features/documents/models/document.model.ts` — `DocumentType` union extended: `'CV' | 'INVOICE' | 'TECHNICAL'`; `DOCUMENT_TYPE_OPTIONS` gains `{ value: 'TECHNICAL', label: 'Technical Document' }`
- [x] `category-form-body` — `isExtractableType` getter (CV + TECHNICAL); LLM extraction toggle visible for both; `onConfirm()` respects toggle for TECHNICAL
- [ ] Document list page: TECHNICAL documents extraction status badge (reuse `ExtractionStatus` component)

---

## Phase 2 — Vector Embeddings & RAG 🔲 NOT STARTED

### 2.1 Database ✅ (migration applied, service layer not yet built)

- [x] Flyway V15: `CREATE EXTENSION IF NOT EXISTS vector` (pgvector)
- [x] Flyway V15: create `document_chunk_embeddings` table
  ```sql
  document_id UUID FK, chunk_index INT, chunk_text TEXT,
  embedding vector(1536), UNIQUE(document_id, chunk_index)
  ```
- [x] HNSW index: `USING hnsw (embedding vector_cosine_ops) WITH (m=16, ef_construction=64)`

> Migration is applied and table exists. No application code uses it yet.

### 2.2 Backend — embedding & RAG 🔲

- [ ] `EmbeddingService` — calls embedding API (OpenAI `text-embedding-3-small` or Ollama `nomic-embed-text`)
- [ ] `DocumentChunkEmbedding` JPA entity + repository with native `<=>` cosine similarity query
- [ ] `RagService` — retrieve top-k chunks → build context → call LLM → return answer + citations
- [ ] `KnowledgeQueryController` — add `POST /api/v1/knowledge/query`
  ```json
  { "question": "What prerequisites does the Kafka guide require?" }
  ```

### 2.3 Python extractor — chunking & embedding 🔲

- [ ] After TECHNICAL extraction, chunk `raw_text` (reuse existing `TextChunker`)
- [ ] Call embedding API per chunk
- [ ] POST embeddings to `/api/v1/knowledge/embeddings`

### 2.4 Frontend — natural language search 🔲

- [ ] Search mode toggle on `/knowledge`: entity search vs RAG query
- [ ] RAG answer panel: LLM answer + source document excerpts with citation links

---

## Phase 3 — Graph Visualization 🔲 NOT STARTED

### 3.1 Frontend 🔲

- [ ] Add `cytoscape` (or `d3-force`) dependency
- [ ] `GraphVisualizationComponent` — force-directed graph rendering entity neighbourhood
- [ ] "Graph view" toggle on entity detail page (`/knowledge/:id`)
- [ ] Node colour by type: Technology (blue), Concept (green), Person (orange), Project (purple), Team (red)
- [ ] Edge labels from `relation_type`

### 3.2 Backend 🔲

- [ ] `GET /api/v1/knowledge/entities/{id}/graph?depth=2` — BFS subgraph up to depth 3
  - Recursive CTE or iterative JPA queries
- [ ] Response shape: `{ nodes: [...], edges: [...] }` (Cytoscape-compatible)

> Note: depth-1 graph is already implemented in `KnowledgeService.getGraph()` and `KnowledgeGraphResponse`. Extend for configurable depth here.

---

## Cross-cutting

- [x] Security: `POST /api/v1/knowledge/ingest` requires `X-Internal-Api-Key` → `ROLE_SERVICE`
- [ ] Entity resolution / deduplication: merge entities with same name (trigram similarity > 0.85) across documents
- [ ] Audit: log every RAG query + answer (question asked, top-k chunk IDs used)
- [ ] Rate limiting: RAG endpoint — 20 req/min per user (LLM cost protection)
- [ ] Integration tests: `KnowledgeIngestControllerTest`, `KnowledgeQueryControllerTest` (Testcontainers + real Postgres)
- [x] Unit tests: `KnowledgeServiceTest` — 32 tests, ≥95% line + branch coverage
- [ ] Angular unit tests: `KnowledgeApi`, entity list + detail pages

---

## Implementation Order Reference

```
✅ 1.1  DB migrations V14 (knowledge graph tables + pg_trgm + indexes)
✅ 1.2  Backend knowledge module (ingest + query + service + 32 unit tests)
✅ 1.3  DocumentType.TECHNICAL enum value (partial — upload flow unchanged)
✅ 1.4  Python extractor TECHNICAL pipeline
✅ 1.5  Frontend knowledge module (entity list + detail + sidebar + category form)
✅ 2.1  DB migration V15 (pgvector + document_chunk_embeddings + HNSW)
        postgres image → pgvector/pgvector:pg16
─── Phase 1 shipped ───────────────────────────────────────────────────────────
🔲 2.2  Backend EmbeddingService + RagService
🔲 2.3  Python extractor chunking + embedding POST
🔲 2.4  Frontend RAG search UI
─── Phase 2 ───────────────────────────────────────────────────────────────────
🔲 3.1  Graph visualization component (Cytoscape/D3)
🔲 3.2  Graph BFS endpoint (depth > 1)
─── Phase 3 ───────────────────────────────────────────────────────────────────
🔲  Cross-cutting: entity resolution, rate limiting, audit log, integration tests
```
