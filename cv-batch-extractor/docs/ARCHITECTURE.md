# CV Batch Extractor — Enterprise Architecture

## 1. Design Principles

### Degrade, Don't Drop
A false rejection (blocking a real CV) is worse than a false pass (forwarding a flagged one). The system must always produce a structured outcome. Backend is always notified — even when a document is rejected or an error occurs.

### Classify, Don't Gatekeep
Guards report what they found (`PASS / WARN / BLOCK`). The pipeline aggregates these into a final status. The backend owns the business decision of what to do with a degraded result (e.g. route to human review queue). The extractor does not make that call.

### One Context Object
A single mutable `PipelineContext` flows through every step of both pipelines. Each step reads from it, writes into it, and appends its `GuardrailReport`. No hidden state; the full audit trail is always on the context.

### Config-Driven Severity
Guard thresholds (max file size, max token chars, min word count, injection pattern list) live in `config.py`. Changing pipeline behavior never requires editing guard code.

### LLM Resilience
Retry logic alone is insufficient under sustained outages. A circuit breaker protects the worker pool from cascading timeout storms.

---

## 2. Processing Status Hierarchy

```
PASS      All guards passed cleanly. HIGH confidence extraction.
DEGRADED  One or more WARNs fired. Data is present but flagged for review.
REJECTED  At least one BLOCK fired. Document is unrecoverable. Goes to dead-letter.
ERROR     Unexpected exception escaped the pipeline. Goes to dead-letter.
```

Backend always receives a callback. The `extractionStatus` and `guardrailWarnings` fields are appended to the existing notification payload.

---

## 3. Full Pipeline Flow

```
File created on disk (Watcher)
        │
        ▼
WorkerPool.submit(document)          ← bounded queue; full queue → dead-letter QUEUE_FULL
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 INPUT PIPELINE  (pre-LLM guards)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  1. FileSizeGuard                   → BLOCK if > max_file_size_mb
  2. MimeTypeGuard                   → BLOCK if magic bytes ≠ expected type
  3. TextExtractor                   → (transformer) populates context.raw_text
  4. TextLengthGuard                 → BLOCK if 0 chars; WARN + truncate if > max_chars
  5. TextQualityGuard                → WARN if word count < min_words or printable ratio < threshold
  6. InjectionGuard                  → WARN + sanitize if LLM-override patterns detected
        │
        │  if any BLOCK → skip LLM entirely
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 LLM SERVICE  (with circuit breaker)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 OUTPUT PIPELINE  (post-LLM guards)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  7. JsonParseGuard                  → BLOCK if raw LLM output cannot be parsed
  8. SchemaGuard                     → BLOCK on hard violations; WARN on soft
  9. SemanticGuard                   → WARN on invalid dates, bad ISO codes, etc.
 10. ConfidenceGuard                 → WARN on MEDIUM or LOW (never BLOCK)
 11. SanitizeGuard                   → (transformer) strips control chars, caps lengths
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 RESULT BUILDER
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  - Aggregate status from all reports
  - Write JSON file (PASS or DEGRADED only)
  - Notify backend (always)
  - Append to dead-letter (REJECTED or ERROR only)
```

---

## 4. Circuit Breaker States (LLM)

```
CLOSED ──(N failures in window)──► OPEN ──(cool-down)──► HALF-OPEN
  ▲                                                           │
  └────────────────(probe success)───────────────────────────┘
                                    (probe failure → OPEN again)
```

While OPEN: all LLM calls fast-fail immediately with a `CircuitOpenError`. Worker submits a REJECTED result without waiting for timeouts. Operator restarts Ollama; after the cool-down period the breaker probes automatically.

Config knobs: `cb_failure_threshold`, `cb_window_seconds`, `cb_cooldown_seconds`.

---

## 5. Worker Pool & Backpressure

```
Watcher (event thread)
    │ submit(task)
    ▼
BoundedQueue (max_queue_size)
    │  if full → dead-letter QUEUE_FULL immediately (don't block watcher)
    ▼
ThreadPoolExecutor (max_workers)
    │  each worker runs Pipeline.run(context)
    ▼
ResultBuilder
```

The watchdog event handler is always non-blocking. Processing happens on worker threads. Queue size and worker count are config values.

---

## 6. File Structure

```
cv-batch-extractor/
├── app/
│   ├── config.py                        # Settings — add guard thresholds, worker knobs
│   ├── domain/
│   │   └── cv_schema.py                 # Pydantic CvExtraction + all nested models
│   ├── guardrails/
│   │   ├── base.py                      # GuardrailReport, PipelineContext, GuardrailPipeline
│   │   ├── input/
│   │   │   ├── __init__.py
│   │   │   ├── file_size.py
│   │   │   ├── mime_type.py
│   │   │   ├── text_extractor.py        # wraps parsers.py as a pipeline step
│   │   │   ├── text_length.py
│   │   │   ├── text_quality.py
│   │   │   └── injection.py
│   │   └── output/
│   │       ├── __init__.py
│   │       ├── json_parse.py            # wraps existing _extract_json logic
│   │       ├── schema.py                # validates against CvExtraction Pydantic model
│   │       ├── semantic.py
│   │       ├── confidence.py
│   │       └── sanitize.py
│   ├── pipeline.py                      # wires both pipelines → ProcessingResult
│   ├── llm_service.py                   # existing + CircuitBreaker wrapper
│   ├── worker.py                        # ThreadPoolExecutor + BoundedQueue
│   ├── dead_letter.py                   # append-only NDJSON log
│   ├── backend_client.py                # add extractionStatus + guardrailWarnings
│   ├── parsers.py                       # unchanged raw parsing functions
│   ├── watcher.py                       # slimmed to: detect file → submit to worker
│   └── __init__.py
├── docs/
│   ├── ARCHITECTURE.md                  # this file
│   ├── GUARDRAILS_SPEC.md               # per-guard specification
│   └── DOMAIN_MODEL.md                  # Pydantic models + data contracts
├── cv_prompt.txt
├── main.py
├── requirements.txt
├── Dockerfile
└── docker-compose.yml
```

---

## 7. Backend Notification Contract

Existing fields are unchanged. Two fields are added:

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

---

## 8. Dead Letter Format

`dead_letter.ndjson` — one JSON object per line, append-only:

```json
{"timestamp": "2026-05-17T10:23:01", "documentId": "uuid", "categoryId": "uuid", "filePath": "/app/uploads/cv/...", "status": "REJECTED", "reason": "mime_type: application/zip is not a supported document type"}
{"timestamp": "2026-05-17T10:24:15", "documentId": "uuid", "categoryId": "uuid", "filePath": "/app/uploads/cv/...", "status": "ERROR", "reason": "RuntimeError: LLM failed after 3 attempts"}
```

Operators replay dead-letter entries by re-dropping the file into the upload directory after fixing the root cause.
