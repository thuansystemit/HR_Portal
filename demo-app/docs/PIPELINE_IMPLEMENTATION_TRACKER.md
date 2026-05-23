# CV Batch Extractor — Pipeline Implementation Tracker

Tracks implementation progress of the full 9-stage document processing pipeline.
See `ARCHITECTURE.md` for system overview.

Last updated: 2026-05-23  
Phase 1 (Quick Wins): ✅ Complete  
Phase 2 (Foundation): ✅ Complete

---

## Pipeline Overview

```
Document Upload          [DONE]
      ↓
Document Classification  [PARTIAL]
      ↓
OCR + Layout Analysis    [PARTIAL]
      ↓
Structured Extraction    [DONE]
      ↓
Semantic Chunking        [TODO]
      ↓
Metadata Enrichment      [PARTIAL]
      ↓
Embedding Pipeline       [TODO]
      ↓
Vector DB / Search Index [TODO]
      ↓
LLM / AI Agents          [TODO]
```

---

## Stage Status

| # | Stage | Status | Owner | Target |
|---|-------|--------|-------|--------|
| 1 | Document Upload | ✅ Done | — | — |
| 2 | Document Classification | ✅ Done | — | — |
| 3 | OCR + Layout Analysis | ✅ Done | — | — |
| 4 | Structured Extraction | ✅ Done | — | — |
| 5 | Semantic Chunking | ✅ Done | — | — |
| 6 | Metadata Enrichment | ✅ Done | — | — |
| 7 | Embedding Pipeline | ⬜ Todo | — | — |
| 8 | Vector DB / Search Index | ⬜ Todo | — | — |
| 9 | LLM / AI Agents | ⬜ Todo | — | — |

Legend: ✅ Done · 🟡 Partial · 🔵 In Progress · ⬜ Todo · ❌ Blocked

---

## Stage 1: Document Upload

**Status:** ✅ Done

### What is implemented
- `watcher.py:DocumentFileHandler` — watchdog `FileSystemEventHandler` watching `{upload_dir}/{type}/{categoryId}/{documentId}/{filename}` recursively.
- `worker.py:WorkerPool` — `ThreadPoolExecutor(max_workers=4)` + `BoundedSemaphore(capacity=104)` for backpressure.
- Dead-letter queue (`dead_letter.py`) — NDJSON append on queue-full or REJECTED/ERROR.
- `main.py` — entry point that wires watcher + pool.

### Remaining gaps
- [ ] No HTTP upload endpoint (requires shared filesystem today).
- [ ] No virus/malware scan (ClamAV / `pyclamd`).
- [ ] No content-hash deduplication (same file uploaded twice processed twice).
- [ ] No WebSocket / callback for upload progress.

---

## Stage 2: Document Classification

**Status:** 🟡 Partial

### What is implemented
- Path-based type routing: `parts[0].lower()` in `watcher.py` determines `"cv"` vs `"invoice"`.
- `MimeTypeGuard` (`guardrails/input/mime_type.py`) — `python-magic` MIME detection; blocks unsupported formats (PDF, DOCX, DOC, JPEG, PNG, TIFF, BMP allowed).

### Remaining gaps
- [ ] **Content-based classifier** — file in wrong directory is processed with wrong schema/prompt.
- [ ] **Language detection** — `rawLanguage` extracted by LLM, not a dedicated detector (`langdetect` / `fasttext`).
- [ ] **Sub-type detection** — tech CV vs academic CV; commercial invoice vs proforma invoice.
- [ ] **Classification confidence** — no score attached to the type decision.

### Implementation plan
1. Add a `DocumentClassifier` guardrail after `LiteParseTextExtractor` / `TextExtractor`.
2. Use a short LLM prompt: *"Is this document a CV, invoice, receipt, or contract? Reply with one word."*
3. If classifier disagrees with path-based type → log WARN, optionally BLOCK.
4. Integrate `langdetect` as a separate step to populate `detectedLanguage` in the context.

**Libraries:** `langdetect`, `transformers` (zero-shot pipeline), or lightweight LLM call.

---

## Stage 3: OCR + Layout Analysis

**Status:** 🟡 Partial

### What is implemented
- `parsers.py:pdf_to_text` — PyMuPDF `page.get_text()` (text layer only, no OCR).
- `parsers.py:image_to_text` — `pytesseract.image_to_string()` for JPEG/PNG/TIFF/BMP.
- `parsers.py:docx_to_text` — `python-docx` paragraph iteration (tables ignored).
- `liteparse_service.py` — `lit parse` CLI with JSON output, falls back to `parsers.extract_text`.
- `TextQualityGuard` — word count + printable-ratio proxy for OCR quality.

### Remaining gaps
- [x] **Scanned-PDF OCR fallback** — if `page.get_text()` empty, render page to pixmap and OCR. **(Done)**
- [x] **DOCX table extraction** — iterate `doc.tables`; line items in invoice DOCX are lost. **(Done)**
- [ ] **PDF layout analysis** — tables, columns, headers/footers, reading order (`page.get_text("dict")` or `page.find_tables()`).
- [ ] **OCR image preprocessing** — deskew, denoise, binarize via `opencv-python`.
- [ ] **Multi-language OCR** — Tesseract defaults to English; add `--lang` config.
- [ ] **Per-word OCR confidence** — use `image_to_data()` instead of `image_to_string()`.

### Implementation plan (quick wins first)
1. **Scanned-PDF fallback** — in `parsers.py:pdf_to_text`, if `page.get_text().strip()` is empty render `page.get_pixmap()` → PIL Image → pytesseract.
2. **DOCX tables** — extend `parsers.py:docx_to_text` to iterate `doc.tables` and serialize cell text as tab-separated rows.
3. **Layout-aware PDF** — switch to `page.get_text("dict")` to recover bounding boxes and block types; detect tables with `page.find_tables()` (PyMuPDF ≥ 1.23).
4. **Preprocessing** — add `cv2` pipeline (grayscale → adaptive threshold → deskew) before `pytesseract.image_to_string`.

**Libraries:** `opencv-python`, `camelot-py`, `tabula-py`, PyMuPDF ≥ 1.23.

---

## Stage 4: Structured Extraction

**Status:** ✅ Done

### What is implemented
- `llm_chain.py` — `ChatOllama` (`format="json"`, `temperature=0`), `PydanticOutputParser`.
- Circuit breaker — CLOSED → OPEN after 5 failures / 60 s; HALF_OPEN after 30 s cooldown.
- Self-correction — on parse failure, re-prompts LLM with error + malformed JSON (up to 3 fix retries).
- Retry loop — up to 3 attempts with exponential backoff.
- `cv_prompt.txt` / `invoice_prompt.txt` — schema-constrained extraction prompts.
- `domain/cv_schema.py:CvExtraction` — full Pydantic v2 schema (16 top-level fields, nested sub-models).
- `domain/invoice_schema.py:InvoiceExtraction` — 12 fields including `vendor`/`buyer`/`lineItems`.

### Remaining gaps
- [x] **Invoice arithmetic validation** — verify `sum(lineItems[].amount) == subtotal`, `subtotal + tax == total`. **(Done)**
- [ ] **Multi-model fallback** — Ollama → cloud LLM (Claude / GPT-4) when Ollama unavailable.
- [ ] **Per-field confidence** — LLM returns single `confidenceOverall`; no field-level scores.
- [ ] **Additional document types** — receipt, contract, purchase order schemas + prompts.
- [ ] **Prompt versioning** — static text files; no A/B testing infrastructure.

### Implementation plan
1. Add `InvoiceArithmeticGuard` output guardrail checking line-item sums and tax/total consistency.
2. Add a secondary `ChatOpenAI` / `ChatAnthropic` chain that is tried when Ollama fails all retries.

**Libraries:** `langchain-openai`, `langchain-anthropic`.

---

## Stage 5: Semantic Chunking

**Status:** ⬜ Todo

### What is needed
Raw text is passed as a single string (truncated at 40,000 chars). Longer documents silently lose data. No section-aware splitting exists.

### Implementation plan
1. Add `SemanticChunker` class in `app/chunking.py`.
2. For CVs — detect section headings (EXPERIENCE, EDUCATION, SKILLS, CERTIFICATIONS, etc.) using regex; split at section boundaries. Overlap last N chars of previous section for continuity.
3. For invoices — split header metadata from line-item block.
4. Each chunk carries metadata: `{page_number, section_name, chunk_index, char_offset_start, char_offset_end}`.
5. Store chunks in `PipelineContext.chunks: list[Chunk]`.
6. Replace the 40k truncation in `TextLengthGuard` with a soft warn; let the chunker handle length.

**Libraries:** `langchain-text-splitters` (`RecursiveCharacterTextSplitter`, `SemanticChunker`).

### Acceptance criteria
- [ ] CVs with > 40,000 chars are fully processed without data loss.
- [ ] Each chunk has metadata (section name, page number, offsets).
- [ ] Unit tests cover: short doc (1 chunk), long multi-section CV, invoice with 50+ line items.

---

## Stage 6: Metadata Enrichment

**Status:** 🟡 Partial

### What is implemented
- `SemanticGuard` — validates email, phone chars, ISO country, ISO language, date ordering. Issues WARNs but does not correct values.
- `SanitizeGuard` — strips control chars, collapses newlines, truncates fields, appends truncated fields to `lowConfidenceFields`.
- `ConfidenceGuard` — surfaces LLM `confidenceOverall` as PASS/WARN.

### Remaining gaps
- [x] **Phone normalization to E.164** — `phonenumbers` library. **(Done)**
- [ ] **Skill/technology normalization** — "ReactJS"/"React.js"/"React" should map to canonical "React". **(Medium)**
- [ ] **Date normalization** — programmatic re-parse after LLM extraction.
- [ ] **Geocoding** — validate city/country pair; optionally enrich with lat/lon.
- [ ] **Company/institution enrichment** — "MIT" → "Massachusetts Institute of Technology".
- [ ] **Duplicate detection** — fingerprint (name + email + phone) to detect re-uploads.

### Implementation plan
1. Add `PhoneNormalizerGuard` using `phonenumbers.parse()` + country hint from `ctx.cv_data.country`.
2. Build `skills_aliases.json` mapping variants to canonical names; add `SkillNormalizerGuard` with `rapidfuzz` for fuzzy matching.
3. Add `DateNormalizerGuard` that re-parses all date strings via `datetime.strptime` to ISO 8601.

**Libraries:** `phonenumbers`, `rapidfuzz`, `geopy`, `pycountry`.

---

## Stage 7: Embedding Pipeline

**Status:** ⬜ Todo

### What is needed
No embedding generation exists anywhere. Required before Stage 8 (Vector DB).

### Implementation plan
1. Choose embedding model:
   - `all-MiniLM-L6-v2` (384-dim, fast, English-first) for speed.
   - `multilingual-e5-large` (1024-dim) for Vietnamese + multilingual support.
   - `OllamaEmbeddings` to stay on the existing local Ollama stack.
2. Add `EmbeddingStage` class in `app/embedding.py`.
3. Generate embeddings for:
   - Full document text (one vector per document).
   - Each semantic chunk (one vector per chunk, from Stage 5).
   - Concatenated key fields: `summary + responsibilities + achievements`.
4. Attach vectors to `PipelineContext.embeddings: dict[str, list[float]]`.
5. Pass to Stage 8 for storage.

**Libraries:** `sentence-transformers`, `langchain-community` (embeddings), `langchain-ollama`.

### Acceptance criteria
- [ ] Embeddings generated for every successfully extracted document.
- [ ] Embedding model is configurable via `settings.embedding_model`.
- [ ] Chunk-level and document-level embeddings are both stored.

---

## Stage 8: Vector DB / Search Index

**Status:** ⬜ Todo

### What is needed
No vector store client exists. Extraction results are JSON files on disk; no search capability.

### Implementation plan
1. Choose vector store: **Qdrant** (Docker Compose native, self-hosted) or **ChromaDB** (simpler, in-process).
2. Add Qdrant service to `docker-compose.yml` (port 6333).
3. Add `VectorStore` wrapper in `app/vector_store.py` using `langchain-qdrant`.
4. On extraction success: upsert document and chunk vectors with payload `{document_id, category_id, document_type, section_name, chunk_index, extracted_fields}`.
5. On document delete: remove all vectors for that `document_id`.
6. Add `settings.vector_store_url` and `settings.vector_store_collection` config keys.

**Libraries:** `qdrant-client`, `langchain-qdrant`.

### Acceptance criteria
- [ ] Vectors upserted to Qdrant after each successful extraction.
- [ ] Vectors deleted when document is removed.
- [ ] Docker Compose includes Qdrant service.
- [ ] Collection is created automatically on startup if it does not exist.

---

## Stage 9: LLM / AI Agents

**Status:** ⬜ Todo

### What is needed
LLM is used for extraction only. No RAG pipeline, no search endpoint, no conversational agent.

### Implementation plan

#### Phase 9a — RAG query endpoint
1. Add `POST /api/v1/search` FastAPI endpoint in `app/api.py`.
2. Accept `{query: str, document_type: str, top_k: int}`.
3. Embed the query using the same model as Stage 7.
4. Retrieve top-k chunks from Qdrant.
5. Pass chunks + query to `ChatOllama` via LangChain `create_retrieval_chain`.
6. Return structured results with sources.

#### Phase 9b — Structured filter agent
1. Build a LangChain agent with tools:
   - `FilterBySkills(skills: list[str])` — vector query + metadata filter.
   - `FilterByLocation(city: str, country: str)` — metadata filter.
   - `FilterByExperience(min_years: int)` — metadata filter.
   - `CompareCandidates(document_ids: list[str])` — retrieve and summarize.
2. Expose via `POST /api/v1/ask` endpoint.

#### Phase 9c — Re-ranking
1. Add a cross-encoder re-ranker (`cross-encoder/ms-marco-MiniLM-L-6-v2`) after retrieval to re-score top-k results.

**Libraries:** `langchain`, `langgraph`, `fastapi`, `uvicorn`, `sentence-transformers` (cross-encoder).

### Acceptance criteria
- [ ] `POST /api/v1/search` returns relevant documents for a natural-language query.
- [ ] `POST /api/v1/ask` handles multi-step queries (e.g., "find React developers in Ho Chi Minh City with 3+ years experience").
- [ ] Results include source document IDs and matched chunk text.
- [ ] Re-ranker improves relevance over raw vector similarity.

---

## Implementation Roadmap

### Phase 1 — Quick Wins ✅ Complete
- [x] Scanned-PDF OCR fallback (`parsers.py`)
- [x] DOCX table extraction (`parsers.py`)
- [x] Invoice arithmetic validation (`guardrails/output/invoice_arithmetic.py`)
- [x] Phone normalization to E.164 (`guardrails/output/phone_normalizer.py`)

### Phase 2 — Foundation ✅ Complete
- [x] Content-based document classifier — `guardrails/input/document_classifier.py`
- [x] Semantic chunking — `chunking.py` + `guardrails/input/chunker.py`
- [x] Skill taxonomy normalization — `guardrails/output/skill_normalizer.py` + `data/skills_aliases.json`
- [x] Date normalization — `guardrails/output/date_normalizer.py`

### Phase 3 — Search Infrastructure (2–4 weeks)
- [ ] Embedding pipeline with configurable model (Stage 7)
- [ ] Qdrant vector store integration + Docker Compose service (Stage 8)
- [ ] FastAPI search endpoint `POST /api/v1/search` (Stage 9a)

### Phase 4 — Agent Layer (4+ weeks)
- [ ] LangChain/LangGraph structured filter agent (Stage 9b)
- [ ] Cross-encoder re-ranking (Stage 9c)
- [ ] Multi-model LLM fallback (Stage 4)
- [ ] Additional document types: receipt, contract (Stage 4)

---

## Dependencies Between Stages

```
Stage 5 (Chunking)
    └── Stage 7 (Embeddings)
            └── Stage 8 (Vector DB)
                    └── Stage 9 (RAG / Agents)

Stage 3 (OCR quality)
    └── Stage 4 (Extraction accuracy)
            └── Stage 6 (Enrichment correctness)
```

Stages 5 → 7 → 8 → 9 must be implemented in order.
Stages 3 and 6 improvements can be done in parallel with any phase.
