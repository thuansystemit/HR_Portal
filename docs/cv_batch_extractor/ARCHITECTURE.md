# Document Batch Extractor — Enterprise Architecture

## 1. Design Principles

### Degrade, Don't Drop
A false rejection (blocking a real CV) is worse than a false pass (forwarding a flagged one). The system must always produce a structured outcome. The backend is always notified — even when a document is rejected or an error occurs.

### Classify, Don't Gatekeep
Validators report what they found (`PASS / WARN / BLOCK`). The pipeline aggregates these into a final status. The backend owns the business decision of what to do with a degraded result (e.g. route to human review queue). The extractor does not make that call.

### One Context Object
A single mutable `PipelineContext` flows through every stage of the pipeline. Each stage reads from it, writes into it, and appends its `ValidationReport`. No hidden state; the full audit trail is always on the context.

### Adapter Pattern for OCR & LLM
OCR engines (LiteParser, PaddleOCR, Tesseract) and LLM providers (Ollama, OpenAI, Azure OpenAI, Anthropic) are behind factory-created adapters. Swapping providers requires only a config change — no pipeline code changes.

### Config-Driven Behaviour
All thresholds (max file size, max token chars, min word count, injection patterns, field length caps) live in `config/settings.py` loaded from environment variables. Changing pipeline behaviour never requires editing stage code.

### LLM Resilience
Retry logic alone is insufficient under sustained outages. A circuit breaker in `OllamaAdapter` protects the worker pool from cascading timeout storms. All LLM adapters share a consistent `complete(prompt) -> str` interface.

---

## 2. Processing Status Hierarchy

```
PASS      All validators passed cleanly. HIGH confidence extraction.
DEGRADED  One or more WARNs fired. Data is present but flagged for review.
REJECTED  At least one BLOCK fired. Document is unrecoverable. Goes to dead-letter.
ERROR     Unexpected exception escaped the pipeline. Goes to dead-letter.
```

The backend always receives a callback regardless of outcome. The `extractionStatus` and `guardrailWarnings` fields are included in every notification payload.

---

## 3. Full Pipeline Flow

The pipeline branches at dispatch time based on the document type encoded in the upload path (`cv/` vs `technical/`). Both paths share the same input validation and OCR stages; they diverge at LLM extraction and backend notification.

```
File created on disk  (ingestion/file_watcher/watcher.py)
        │  path: {upload_dir}/{documentType}/{categoryId}/{documentId}/{filename}
        │  routed types: cv, technical
        ▼
WorkerPool.submit(document_id, category_id, file_path, document_type)
    ← bounded semaphore; capacity = max_workers + queue_size
    ← if full → dead-letter QUEUE_FULL + notify backend immediately
        │
        ▼
ExtractionPipeline.run(document_id, category_id, file_path, document_type)
        │
        ├─── document_type == "CV" ──────────────────────────────────────────────────┐
        │                                                                            │
        ▼                                                                            ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 CV PATH (_run_stages)                             TECHNICAL PATH (_run_stages_technical)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

 STAGE 1 — Document Classification                 (skipped — type known from path)
   ctx.document_type = "pdf"|"docx"|"image"|…

 STAGE 2 — File Input Validation                   STAGE 1 — File Input Validation
   FileSizeValidator  → BLOCK if > max MB             (same validators)
   MimeTypeValidator  → BLOCK if not allowed MIME

 STAGE 3 — Preprocessing                           STAGE 2 — Preprocessing
   image: enhance → deskew → denoise                 (same pipeline, type="technical")
   doc/docx: LibreOffice → PDF

 STAGE 4 — OCR                                     STAGE 3 — OCR
   OCRAdapter.extract()                               (same adapters)
   → ctx.raw_text, ctx.ocr_confidence

 STAGE 5 — Text Input Validation                   STAGE 4 — Text Input Validation
   TextLengthValidator                                (same validators)
   TextQualityValidator
   InjectionValidator → ctx.prompt_text

 STAGE 6 — Layout, Entity & Metadata               (skipped for TECHNICAL)
   LayoutAnalyzer, TableExtractor,
   EntityExtractor, MetadataExtractor

 STAGE 7 — Chunking                                STAGE 5 — Chunking
   TextChunker.chunk()                                TextChunker.chunk()

 STAGE 8 — LLM Extraction (CV prompt)              STAGE 6 — LLM Extraction (technical prompt)
   prompt_templates/cv_extraction.py                  prompt_templates/technical_extraction.py
   → ctx.llm_raw                                      → ctx.llm_raw

 STAGE 9 — Output Validation (CV schema)           STAGE 7 — Output Validation (knowledge schema)
   SchemaValidator (json_parse + CvExtraction)        json.loads → KnowledgeExtraction.model_validate
   ConfidenceScorer                                   BLOCK on parse / Pydantic failure
   HallucinationChecker                               → ctx.knowledge_data

 STAGE 10 — Normalisation & Enrichment             (skipped for TECHNICAL)
   Normalizer, Enricher

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 RESULT BUILDER (shared)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  - Aggregate status from all ValidationReports
  - CV:        write cv_{name}_{id}.json  (PASS/DEGRADED) → notify POST /api/v1/cv-candidates
  - TECHNICAL: write tech_{title}_{id}.json (PASS/DEGRADED) → notify POST /api/v1/knowledge/ingest
  - Append to dead_letter.ndjson (REJECTED or ERROR only)
```

---

## 4. Circuit Breaker States (Ollama Adapter)

```
CLOSED ──(N failures in window)──► OPEN ──(cool-down expires)──► HALF-OPEN
  ▲                                                                    │
  └─────────────────────(probe success)───────────────────────────────┘
                                          (probe failure → OPEN again)
```

While `OPEN`: all LLM calls fast-fail with `CircuitOpenError`. The pipeline emits a `REJECTED` result without waiting for timeouts.

Config knobs: `cb_failure_threshold`, `cb_window_seconds`, `cb_cooldown_seconds`.

---

## 5. Worker Pool & Backpressure

```
Watcher event thread
    │ submit(document_id, category_id, file_path, document_type)
    ▼
BoundedSemaphore  (capacity = max_workers + queue_size)
    │  if acquire fails → dead-letter QUEUE_FULL + notify backend immediately
    ▼
ThreadPoolExecutor  (max_workers)
    │  each worker thread runs ExtractionPipeline.run(…, document_type)
    ▼
orchestration._process_task()
    ├─ document_type == "CV"        → _notify_cv_backend()
    └─ document_type == "TECHNICAL" → _notify_knowledge_backend()
    └─ _dead_letter() (if REJECTED or ERROR)
```

The watchdog event handler is always non-blocking. Processing is on worker threads. Both `worker_max_workers` and `worker_queue_size` are environment-variable config.

---

## 6. Enterprise Directory Structure

```
cv-batch-extractor/
├── main.py                              # entry point: configure logging → WorkerPool → Watcher
├── config/
│   └── settings.py                      # Pydantic BaseSettings — all knobs via env vars
├── domain/
│   ├── cv_schema.py                     # CvExtraction Pydantic model + nested types
│   ├── technical_schema.py              # KnowledgeExtraction + TechEntity, ConceptEntity, Relationship
│   └── models.py                        # PipelineContext, ProcessingResult, ValidationReport
├── ingestion/
│   ├── file_watcher/watcher.py          # Watchdog observer — routes cv/ and technical/ paths
│   ├── document_classifier/classifier.py # python-magic + extension fallback
│   └── upload_service/service.py        # (stub) multipart upload helper
├── preprocessing/
│   ├── pipeline.py                      # routes file type to sub-steps
│   ├── format_converter/converter.py    # LibreOffice DOC/DOCX → PDF
│   ├── image_enhancement/enhancer.py   # PIL contrast/sharpness
│   ├── deskew/deskewer.py               # OpenCV rotation correction
│   ├── denoise/denoiser.py              # OpenCV Gaussian blur
│   └── page_splitter/splitter.py        # PyMuPDF page splitting
├── ocr/
│   ├── base.py                          # OCRResult dataclass + OCRAdapter Protocol
│   ├── ocr_factory.py                   # create(engine_name) → OCRAdapter
│   ├── liteparser_adapter/adapter.py    # LiteParser CLI + builtin fallback
│   ├── paddleocr_adapter/adapter.py     # PaddleOCR (stub)
│   └── tesseract_adapter/adapter.py     # pytesseract + PyMuPDF renderer
├── extraction/
│   ├── layout_analysis/analyzer.py      # section headings, paragraph detection
│   ├── table_extraction/extractor.py    # table row/cell extraction (stub)
│   ├── entity_extraction/extractor.py   # regex email, phone extraction
│   ├── key_value_extraction/extractor.py # key: value pair detection (stub)
│   └── metadata_extraction/extractor.py # PyMuPDF document metadata
├── chunking/
│   ├── base.py                          # Chunker Protocol
│   ├── text_chunker.py                  # fixed-size with overlap (default)
│   ├── semantic_chunker.py              # section heading boundaries
│   └── page_chunker.py                  # form-feed (\x0c) page splitting
├── llm/
│   ├── base.py                          # LLMAdapter Protocol
│   ├── llm_factory.py                   # create(provider_name) → LLMAdapter
│   ├── ollama_adapter.py                # OllamaAdapter with CircuitBreaker
│   ├── openai_adapter.py                # OpenAIAdapter
│   ├── azure_openai_adapter.py          # AzureOpenAIAdapter
│   ├── anthropic_adapter.py             # AnthropicAdapter
│   └── prompt_templates/
│       ├── cv_extraction.py             # CV extraction prompt — build(text) → str
│       └── technical_extraction.py      # Knowledge graph extraction prompt — build(text) → str
├── validation/
│   ├── schema_validator.py              # JSON parse + Pydantic model_validate (CV only)
│   ├── confidence_scoring.py            # HIGH/MEDIUM/LOW/absent scoring (CV only)
│   └── hallucination_checker.py         # name/email grounding, GPA, future dates (CV only)
├── postprocessing/
│   ├── normalization/normalizer.py      # strip control chars, cap field lengths (CV only)
│   ├── enrichment/enricher.py           # (stub) geocoding, skill tagging (CV only)
│   └── data_mapping/mapper.py           # (stub) field remapping
├── storage/
│   └── vector_db/client.py              # (stub) Qdrant/pgvector embedding store
├── workflow/
│   ├── extraction_pipeline.py           # ExtractionPipeline — CV + TECHNICAL paths
│   ├── orchestration.py                 # WorkerPool + _notify_cv_backend + _notify_knowledge_backend + _dead_letter
│   └── retry_handler.py                 # exponential backoff decorator
├── monitoring/
│   ├── logging/setup.py                 # configure(level, fmt) — text or JSON
│   ├── metrics/collector.py             # MetricsCollector — counters + histograms
│   └── tracing/tracer.py                # Tracer.start_span() — OTEL-ready
├── tests/                               # test suite (pytest)
├── requirements.txt
├── Dockerfile
└── docker-compose.yml
```

---

## 7. Backend Notification Contracts

### 7.1 CV Documents — POST `/api/v1/cv-candidates`

Always called regardless of outcome:

```json
{
  "documentId": "uuid",
  "documentCategoryId": "uuid",
  "jsonFile": "cv_john_doe_abc123.json",
  "extractionStatus": "PASS | DEGRADED | REJECTED | ERROR",
  "guardrailWarnings": [
    "text_quality: low word count (42 words, min 50)",
    "confidence: MEDIUM — partial data extracted"
  ]
}
```

`jsonFile` is `null` when `extractionStatus` is `REJECTED` or `ERROR`.

### 7.2 Technical Documents — POST `/api/v1/knowledge/ingest`

Always called regardless of outcome. Requires `X-Internal-Api-Key` header (`ROLE_SERVICE`):

```json
{
  "documentId": "uuid",
  "categoryId": "uuid",
  "extractionStatus": "PASS | DEGRADED | REJECTED | ERROR",
  "guardrailWarnings": [],
  "documentType": "TECHNICAL",
  "title": "Microservices Architecture Overview",
  "summary": "Describes the service decomposition and inter-service communication patterns.",
  "technologies": [
    { "name": "PostgreSQL", "version": "16", "category": "database", "aliases": ["Postgres"] },
    { "name": "Spring Boot", "version": "3.3", "category": "framework", "aliases": [] }
  ],
  "concepts": [
    { "name": "Circuit Breaker", "definition": "Fault-tolerance pattern that stops cascading failures.", "relatedConcepts": ["Retry", "Bulkhead"] }
  ],
  "relationships": [
    { "source": "Spring Boot", "target": "PostgreSQL", "relationType": "stores_in", "weight": 1.0 }
  ]
}
```

On `REJECTED` or `ERROR`, `technologies`, `concepts`, and `relationships` are empty arrays; the backend marks the document `FAILED`.

### 7.3 Status Mapping (both document types)

| Extractor status | Document.extractionStatus |
|---|---|
| `PASS` / `DEGRADED` | `COMPLETED` |
| `REJECTED` / `ERROR` | `FAILED` |

---

## 8. Dead Letter Format

`output/dead_letter.ndjson` — one JSON object per line, append-only:

```json
{"timestamp": "2026-05-17T10:23:01", "documentId": "uuid", "categoryId": "uuid", "filePath": "/app/uploads/cv/...", "status": "REJECTED", "reason": "mime_type: 'application/zip' is not a supported document type"}
{"timestamp": "2026-05-17T10:24:15", "documentId": "uuid", "categoryId": "uuid", "filePath": "/app/uploads/cv/...", "status": "ERROR",    "reason": "RuntimeError: LLM failed after 3 attempts"}
{"timestamp": "2026-05-17T10:25:03", "documentId": "uuid", "categoryId": "uuid", "filePath": "/app/uploads/cv/...", "status": "REJECTED", "reason": "worker_pool: queue full, document could not be scheduled"}
```

Replay by re-dropping the file into the upload directory after fixing the root cause.

---

## 9. Monitoring

| Component | Module | Notes |
|---|---|---|
| Structured logging | `monitoring/logging/setup.py` | `fmt="text"` or `fmt="json"` (ELK-ready) |
| Metrics | `monitoring/metrics/collector.py` | `document_processing_seconds`, `ocr_seconds`, `llm_seconds` histograms; per-status counters |
| Tracing | `monitoring/tracing/tracer.py` | `tracer.start_span("extraction")` wraps the full pipeline run; OTEL-ready |
