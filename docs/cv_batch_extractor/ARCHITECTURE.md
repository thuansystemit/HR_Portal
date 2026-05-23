# CV Batch Extractor — Enterprise Architecture

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

## 3. Full Pipeline Flow (10 Stages)

```
File created on disk  (ingestion/file_watcher/watcher.py)
        │
        ▼
WorkerPool.submit(document_id, category_id, file_path)
    ← bounded semaphore; capacity = max_workers + queue_size
    ← if full → dead-letter QUEUE_FULL + notify backend immediately
        │
        ▼
ExtractionPipeline.run(document_id, category_id, file_path)
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STAGE 1 — Document Classification
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  DocumentClassifier.classify(file_path)
  → ctx.document_type = "pdf" | "docx" | "doc" | "image" | "unknown"
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STAGE 2 — File Input Validation
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  FileSizeValidator    → BLOCK if > max_file_size_mb
  MimeTypeValidator    → BLOCK if magic bytes ≠ allowed_mime_types
  ─ if BLOCK → skip to ResultBuilder
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STAGE 3 — Preprocessing
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  PreprocessingPipeline.process(file_path, document_type)
  → image:    enhance → deskew → denoise
  → doc/docx: FormatConverter (LibreOffice) → PDF
  → ctx.preprocessed_path
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STAGE 4 — OCR
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  OCRAdapter.extract(preprocessed_path)   [factory: ocr_factory.create(settings.ocr_engine)]
  → liteparser:   LiteParser CLI → builtin fallback (PyMuPDF / python-docx / pytesseract)
  → paddleocr:    PaddleOCR engine
  → tesseract:    pytesseract + PyMuPDF page renderer
  → ctx.raw_text, ctx.ocr_confidence
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STAGE 5 — Text Input Validation
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  TextLengthValidator  → BLOCK if < min_text_chars; WARN + truncate if > max_text_chars
  TextQualityValidator → WARN if word_count < min_word_count or printable_ratio < threshold
  InjectionValidator   → WARN + redact if LLM-override patterns detected → ctx.prompt_text
  ─ if BLOCK → skip to ResultBuilder
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STAGE 6 — Layout, Entity & Metadata Extraction
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  LayoutAnalyzer.analyze()     → ctx.layout
  TableExtractor.extract()     → ctx.tables
  EntityExtractor.extract()    → ctx.entities  (email, phone via regex)
  MetadataExtractor.extract()  → ctx.doc_metadata  (PyMuPDF metadata)
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STAGE 7 — Chunking
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  TextChunker.chunk(prompt_text or raw_text)
  → ctx.chunks  (chunk_size / chunk_overlap from settings)
  Available: TextChunker (fixed-size), SemanticChunker (section headings), PageChunker (form-feeds)
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STAGE 8 — LLM Extraction  (circuit breaker guarded)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  LLMAdapter.complete(prompt)   [factory: llm_factory.create(settings.llm_provider)]
  → ollama:       OllamaAdapter with CircuitBreaker (CLOSED/OPEN/HALF-OPEN)
  → openai:       OpenAIAdapter
  → azure_openai: AzureOpenAIAdapter
  → anthropic:    AnthropicAdapter
  → ctx.llm_raw
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STAGE 9 — Output Validation
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  SchemaValidator.validate(ctx)       → BLOCK on hard schema failure; WARN on soft
    ├─ JsonParseStep  → ctx.raw_dict   (BLOCK if LLM output cannot be parsed)
    └─ ModelValidateStep → ctx.cv_data (CvExtraction Pydantic model)
  ConfidenceScorer.validate(ctx)      → WARN on MEDIUM/LOW/absent confidence
  HallucinationChecker.validate(ctx)  → WARN on grounding failures (name, email, GPA, dates)
  ─ if BLOCK → skip to ResultBuilder
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STAGE 10 — Normalisation & Enrichment
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Normalizer.normalize(ctx)   → strip control chars, collapse newlines, cap field lengths
  Enricher.enrich(ctx)        → (stub) geocoding, skill taxonomy tagging
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 RESULT BUILDER
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  - Aggregate status from all ValidationReports
  - Write JSON file to output_dir (PASS or DEGRADED only)
  - Notify backend via POST /api/v1/cv-candidates (always)
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
    │ submit(document_id, category_id, file_path)
    ▼
BoundedSemaphore  (capacity = max_workers + queue_size)
    │  if acquire fails → dead-letter QUEUE_FULL + notify backend immediately
    ▼
ThreadPoolExecutor  (max_workers)
    │  each worker thread runs ExtractionPipeline.run()
    ▼
orchestration._process_task()  →  _notify_backend()  →  _dead_letter() (if needed)
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
│   └── models.py                        # PipelineContext, ProcessingResult, ValidationReport
├── ingestion/
│   ├── file_watcher/watcher.py          # Watchdog observer — emits events to WorkerPool
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
│   └── prompt_templates/cv_extraction.py # build(text) → prompt string
├── validation/
│   ├── schema_validator.py              # JSON parse + Pydantic model_validate
│   ├── confidence_scoring.py            # HIGH/MEDIUM/LOW/absent scoring
│   └── hallucination_checker.py         # name/email grounding, GPA, future dates
├── postprocessing/
│   ├── normalization/normalizer.py      # strip control chars, cap field lengths
│   ├── enrichment/enricher.py           # (stub) geocoding, skill tagging
│   └── data_mapping/mapper.py           # (stub) field remapping
├── storage/
│   └── vector_db/client.py              # (stub) Qdrant/pgvector embedding store
├── workflow/
│   ├── extraction_pipeline.py           # ExtractionPipeline — 10-stage orchestrator
│   ├── orchestration.py                 # WorkerPool + _notify_backend + _dead_letter
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

## 7. Backend Notification Contract

POST `/api/v1/cv-candidates` — always called regardless of outcome:

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
`guardrailWarnings` is `[]` when `extractionStatus` is `PASS`.

The backend maps extractor statuses to document lifecycle statuses:

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
