# Enterprise CV Management Platform

A full-stack document management system with automated CV extraction powered by a local LLM (Ollama). Upload CVs, extract structured candidate profiles automatically, and browse candidates through a rich Angular UI.

---

## Architecture

```
┌──────────────────────┐   REST / JWT    ┌───────────────────────────────────────┐
│   Angular 21 UI      │ ◀────────────▶  │         Spring Boot Backend           │
│   (port 4200)        │                 │         (port 8080)                   │
│                      │                 │                                       │
│  • Document mgmt     │                 │  • Auth (JWT + roles)                 │
│  • Category config   │                 │  • Document storage                   │
│    (extraction mode  │                 │  • CV candidate API                   │
│     per category)    │                 │  • PostgreSQL (Flyway)                │
│  • CV candidates     │                 │  • Redis cache                        │
└──────────────────────┘                 └───────────┬───────────────────────────┘
                                                     │
                              upload stored to shared volume
                                                     │
                                                     ▼
                                         ┌───────────────────────┐
                                         │  /app/uploads/        │
                                         │  cv/{catId}/{docId}/  │
                                         └───────────┬───────────┘
                                                     │ watchdog detects new file
                                                     ▼
                         ┌───────────────────────────────────────────────────────┐
                         │                  cv-batch-extractor                   │
                         │                                                       │
                         │  Watcher ──▶ WorkerPool (4 threads, queue 100)       │
                         │                    │                                  │
                         │          ┌─────────▼──────────┐                      │
                         │          │   INPUT PIPELINE    │                      │
                         │          │  1. FileSizeGuard   │                      │
                         │          │  2. MimeTypeGuard   │                      │
                         │          │  3a. TextExtractor  │ ← llmExtraction=true │
                         │          │  3b. LiteParser     │ ← llmExtraction=false│
                         │          │  4. TextLengthGuard │                      │
                         │          │  5. TextQuality     │                      │
                         │          │  6. InjectionGuard  │                      │
                         │          └─────────┬──────────┘                      │
                         │                    │ (skip if BLOCK)                  │
                         │                    ▼                                  │
                         │          ┌─────────────────────┐                     │
                         │          │  LLM Service        │                     │
                         │          │  + Circuit Breaker  │ ──▶ Ollama :11434   │
                         │          │  + Retry (3×)       │                     │
                         │          └─────────┬───────────┘                     │
                         │                    ▼                                  │
                         │          ┌─────────────────────┐                     │
                         │          │  OUTPUT PIPELINE    │                     │
                         │          │  7.  JsonParseGuard │                     │
                         │          │  8.  SchemaGuard    │                     │
                         │          │  9.  SemanticGuard  │                     │
                         │          │  10. ConfidenceGuard│                     │
                         │          │  11. SanitizeGuard  │                     │
                         │          └─────────┬───────────┘                     │
                         │                    │                                  │
                         │          ┌─────────▼──────────────────────┐          │
                         │          │         Result Builder          │          │
                         │          │  PASS / DEGRADED → write JSON  │          │
                         │          │  REJECTED / ERROR → dead-letter │          │
                         │          └─────────┬──────────────────────┘          │
                         └─────────────────────┼─────────────────────────────────┘
                                               │
                          ┌────────────────────┼───────────────────┐
                          │                    │                   │
                          ▼                    ▼                   ▼
              ┌───────────────────┐ ┌──────────────────┐ ┌─────────────────────┐
              │  /app/output/     │ │  Spring Boot     │ │  dead_letter.ndjson │
              │  cv_name_id.json  │ │  POST /cv-cands  │ │  (REJECTED / ERROR) │
              └───────────────────┘ └────────┬─────────┘ └─────────────────────┘
                                             │
                                             ▼
                                    ┌─────────────────┐
                                    │   PostgreSQL    │
                                    │  cv_candidates  │
                                    └─────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Angular 21, Bootstrap 5, Angular Signals |
| Backend | Spring Boot 3.3.5, Java 21, Spring Security (JWT) |
| Database | PostgreSQL 16, Flyway migrations |
| Cache | Redis 7 |
| CV Extraction | Python 3.11, watchdog, PyMuPDF, pytesseract, python-docx, @llamaindex/liteparse |
| LLM | Ollama (llama3) |
| Infrastructure | Docker, Docker Compose |

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Docker Desktop | Latest | Required for backend stack |
| Node.js | 18+ | Required for frontend |
| npm | 9+ | Comes with Node.js |
| Ollama | Latest | Required for CV extraction |

---

## Step 1 — Install and Configure Ollama

Ollama runs the local LLM that extracts structured data from CV text.

### Install Ollama

**macOS**
```bash
brew install ollama
```
Or download the installer from https://ollama.com/download

**Linux**
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

**Windows**
Download the installer from https://ollama.com/download

### Start the Ollama server
```bash
ollama serve
```
Ollama listens on `http://localhost:11434` by default. Keep this terminal open (or run it as a background service).

### Pull the required model
```bash
ollama pull llama3
```
This downloads ~4.7 GB. Wait for the download to complete before uploading any CVs.

### Verify Ollama is running
```bash
curl http://localhost:11434/api/tags
```
You should see `llama3` in the response.

---

## Step 2 — Start the Backend

The backend stack (Spring Boot + PostgreSQL + Redis + cv-batch-extractor) is fully containerised.

```bash
cd demo-app-backend
docker compose up -d --build
```

This starts 4 containers:

| Container | Port | Description |
|---|---|---|
| `demo-app-backend-app-1` | 8080 | Spring Boot REST API |
| `demo-app-backend-postgres-1` | 5432 | PostgreSQL database |
| `demo-app-backend-redis-1` | 6379 | Redis cache |
| `demo-app-backend-cv-extractor-1` | — | CV extraction watcher |

### Wait for the backend to be healthy
```bash
docker compose logs -f app
```
Look for `Started DemoAppApplication` before proceeding.

### Check all containers are running
```bash
docker compose ps
```

### Environment variables (optional overrides)

Create a `.env` file in `demo-app-backend/` to override defaults:

```env
# Service-to-service shared secret (cv-extractor → backend)
INTERNAL_API_KEY=change-me-in-production

# Point to a different Ollama instance
OLLAMA_URL=http://host.docker.internal:11434
OLLAMA_MODEL=llama3
```

### Useful log commands
```bash
# Backend API logs
docker compose logs -f app

# CV extractor logs (watch extraction pipeline)
docker compose logs -f cv-extractor

# Database error logs
docker compose logs -f postgres
```

---

## Step 3 — Start the Frontend

```bash
cd demo-app
npm install
npm start
```

The Angular dev server starts at **http://localhost:4200**.

### Default login credentials

| Field | Value |
|---|---|
| Email | `admin@demo.com` |
| Password | `password` |

> Check the Flyway seed migration in `demo-app-backend/src/main/resources/db/migration/` for the exact seeded credentials.

---

## Step 4 — Upload a CV and See Extraction in Action

1. Log in at http://localhost:4200
2. Navigate to **Document Management**
3. Create a category with type **CV** — choose the extraction mode (see below)
4. Upload a PDF or DOCX CV file
5. Watch the cv-extractor logs process it:
   ```bash
   docker compose logs -f cv-extractor
   ```
6. After extraction completes, click **View Candidates** on the CV category to see the structured profile

---

## CV Extraction Pipeline

The extractor is enterprise-grade with an 11-guard pipeline, a circuit breaker, and a bounded worker pool.

### Extraction Modes

Each CV category has a **"Use LLM extraction"** toggle (visible in the category form when type = CV):

| Mode | Text Extractor | LLM Called | When to Use |
|---|---|---|---|
| **LLM** (default, toggle ON) | Tesseract / PDFMiner / python-docx | Yes | Standard — full structured extraction |
| **LiteParse** (toggle OFF) | `@llamaindex/liteparse` CLI | Yes | Higher-fidelity raw text fed to the same LLM |

Both modes run through the identical output guardrails. The only difference is which text extractor is used before the LLM call.

### Full Pipeline Flow

```
CV file detected on disk (watchdog)
        │
        ▼
WorkerPool.submit()          ← if queue full → dead-letter immediately
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 INPUT PIPELINE  (pre-LLM guardrails)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  1. FileSizeGuard         → BLOCK if > 20 MB
  2. MimeTypeGuard         → BLOCK if magic bytes ≠ PDF/DOCX/image
  3. TextExtractor         → (LLM mode)      populate ctx.raw_text via Tesseract/PDFMiner
     LiteParseExtractor    → (LiteParse mode) populate ctx.raw_text via lit CLI
  4. TextLengthGuard       → BLOCK if < 50 chars; WARN + truncate if > 40 000
  5. TextQualityGuard      → WARN if word count < 30 or printable ratio < 0.85
  6. InjectionGuard        → WARN + redact if LLM prompt-override patterns found
        │
        │  (skip if any BLOCK)
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 LLM SERVICE  (Ollama + circuit breaker + retry)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 OUTPUT PIPELINE  (post-LLM guardrails)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  7. JsonParseGuard        → BLOCK if LLM output cannot be parsed as JSON
  8. SchemaGuard           → BLOCK on hard violations; WARN on soft
  9. SemanticGuard         → WARN on bad dates, invalid ISO codes, bad email/phone
 10. ConfidenceGuard       → WARN on MEDIUM or LOW self-reported confidence
 11. SanitizeGuard         → strip control chars, cap field lengths
        │
        ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 RESULT BUILDER
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ├── Write JSON to /app/output/  (PASS or DEGRADED)
  ├── Notify backend              (always)
  └── Append to dead_letter.ndjson (REJECTED or ERROR)
```

### Processing Statuses

| Status | Meaning |
|---|---|
| `PASS` | All guards passed. Confidence HIGH. |
| `DEGRADED` | One or more WARNs fired. Data present but flagged for review. |
| `REJECTED` | At least one BLOCK. Document unrecoverable. Written to dead-letter. |
| `ERROR` | Unhandled exception escaped the pipeline. Written to dead-letter. |

### Circuit Breaker (LLM resilience)

Prevents timeout storms when Ollama is down:

```
CLOSED ──(5 failures / 60 s)──► OPEN ──(30 s cooldown)──► HALF-OPEN
  ▲                                                              │
  └─────────────────(probe success)─────────────────────────────┘
```

While OPEN, LLM calls fast-fail immediately — no retries, no blocking threads.

---

## Project Structure

```
Angular_Enterprise/
├── demo-app/                        # Angular 21 frontend
│   └── src/app/
│       ├── core/                    # Guards, interceptors, global services
│       ├── shared/                  # Reusable UI components, pipes, utils
│       ├── layout/                  # Shell, header, sidebar
│       └── features/
│           ├── documents/           # Document & category management
│           ├── cv-candidates/       # CV candidate profiles
│           ├── users/               # User management
│           ├── roles/               # Role & permission management
│           ├── reports/             # Analytics & reports
│           └── dashboard/           # Dashboard
│
├── demo-app-backend/                # Spring Boot backend
│   └── src/main/java/com/demo/app/
│       ├── config/                  # Security, CORS, storage config
│       ├── content/                 # Document & category domain
│       ├── cv/                      # CV candidate domain + extraction
│       ├── iam/                     # Users, roles, permissions, JWT
│       ├── insights/                # Reports & analytics
│       └── platform/                # Shared exceptions, handlers
│
└── cv-batch-extractor/              # Python CV extraction service
    ├── app/
    │   ├── config.py                # All settings (pydantic-settings)
    │   ├── domain/cv_schema.py      # CvExtraction Pydantic model
    │   ├── guardrails/
    │   │   ├── base.py              # GuardrailReport, PipelineContext, GuardrailPipeline
    │   │   ├── input/               # 6 pre-LLM guards (file_size, mime, extractor…)
    │   │   └── output/              # 5 post-LLM guards (json_parse, schema, semantic…)
    │   ├── pipeline.py              # Wires input + LLM + output → ProcessingResult
    │   ├── liteparse_service.py     # Calls `lit parse` CLI, falls back to parsers
    │   ├── llm_service.py           # Ollama client + CircuitBreaker
    │   ├── worker.py                # ThreadPoolExecutor + bounded queue
    │   ├── dead_letter.py           # Append-only NDJSON rejection log
    │   ├── backend_client.py        # Notify backend + fetch extraction mode
    │   ├── parsers.py               # PDF / DOCX / image raw text extraction
    │   └── watcher.py               # Watchdog observer → worker.submit()
    └── docs/
        ├── ARCHITECTURE.md          # Detailed design decisions
        ├── GUARDRAILS_SPEC.md       # Per-guard specification
        └── DOMAIN_MODEL.md          # Pydantic models + data contracts
```

---

## Stopping the Stack

```bash
cd demo-app-backend
docker compose down
```

To also remove all data volumes (database, uploads, extracted JSON):
```bash
docker compose down -v
```

---

## Troubleshooting

**cv-extractor cannot reach Ollama**
```
[Errno 101] Network is unreachable
```
- Ensure `ollama serve` is running on the host
- Verify: `curl http://localhost:11434/api/tags`

**Backend returns 403 on cv-candidates**
- Check `INTERNAL_API_KEY` is the same value in both `app` and `cv-extractor` environment sections of `docker-compose.yml`

**LLM returns invalid JSON**
- The `JsonParseGuard` strips markdown fences and finds the outermost `{...}` block before giving up
- If it still fails, check `docker compose logs cv-extractor` for the raw LLM output — the document gets dead-lettered as `REJECTED`

**LiteParse mode produces empty text**
- The service falls back to `parsers.py` automatically if the `lit` CLI fails
- Check `docker compose logs cv-extractor` for `liteparse_extractor` BLOCK messages

**Null constraint violations in PostgreSQL**
- The mapper applies `"Unknown"` fallbacks for all `@NotBlank` fields
- Check `docker compose logs postgres` for the specific column name

**Frontend cannot connect to backend**
- Confirm backend is healthy: `curl http://localhost:8080/actuator/health`
- Check CORS: `CORS_ORIGINS` must include `http://localhost:4200`
