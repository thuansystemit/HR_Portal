# Enterprise HR Platform

A full-stack HR management system covering the complete hiring lifecycle — from **hiring requests** and **CV extraction** to **candidate search**, **recruitment pipeline**, **CV sharing with Dev Team**, and **HR analytics**. Built on Angular 21 + Spring Boot 3 + PostgreSQL, with an automated CV extraction microservice powered by a local LLM (Ollama).

> **Product backlog, workflow map, gap analysis, and sprint planning:** [`docs/HR_PLATFORM.md`](docs/HR_PLATFORM.md)

---

## End-to-End Hiring Workflow

```
Dev Team submits Hiring Request
        │
        ▼
HR creates Job Posting  ──▶  HR uploads CVs  ──▶  Automated Extraction
                                                          │
                                                          ▼
                                                  Candidate Pool
                                                  (hiring_status badge)
                                                          │
                                          HR searches & shortlists
                                                          │
                                          HR shares CV with Dev Team
                                                          │
                                          Dev Team marks impression
                                          (Interested / Not / Later)
                                                          │
                                          HR applies to Job Posting
                                                          │
                                   Kanban: Applied → Screening → Interview
                                                          │
                                          HR schedules Interview
                                          Dev Team submits Feedback
                                                          │
                                          HR decides: Hire / Reject
                                                          │
                                          hiring_status auto-updated
```

---

## Architecture

```
┌──────────────────────────┐   REST / JWT   ┌─────────────────────────────────────────┐
│   Angular 21 UI          │ ◀───────────▶  │         Spring Boot Backend             │
│   (port 4200)            │                │         (port 8080)                     │
│                          │                │                                         │
│  • Document management   │                │  • Auth — JWT cookie (access-token)     │
│  • CV candidate profiles │                │  • IAM — users, roles, PERM_* grants    │
│  • Candidate search      │                │  • Content — documents, categories      │
│  • Hiring requests       │                │  • CV — candidates, extraction ingest   │
│  • CV sharing (inbox)    │                │  • Hiring — requests, cv_shares         │
│  • Recruitment Kanban    │                │  • Recruitment — postings, applications │
│  • HR Analytics          │                │  • Insights — analytics, reports        │
│  • Knowledge Base        │                │  • Knowledge — graph entities           │
└──────────────────────────┘                └──────────────┬──────────────────────────┘
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
                           │                    ▼                                  │
                           │          ┌─────────────────────────────────┐         │
                           │          │         Result Builder           │         │
                           │          │  PASS / DEGRADED → write JSON   │         │
                           │          │  REJECTED / ERROR → dead-letter  │         │
                           │          └─────────┬───────────────────────┘         │
                           └─────────────────────┼─────────────────────────────────┘
                                                 │
                              ┌──────────────────┼──────────────────┐
                              │                  │                  │
                              ▼                  ▼                  ▼
                  ┌───────────────────┐ ┌─────────────────┐ ┌─────────────────────┐
                  │  /app/output/     │ │  Spring Boot    │ │  dead_letter.ndjson │
                  │  cv_name_id.json  │ │  POST /cv-cands │ │  (REJECTED / ERROR) │
                  └───────────────────┘ └────────┬────────┘ └─────────────────────┘
                                                 │
                                                 ▼
                                        ┌─────────────────┐
                                        │   PostgreSQL    │
                                        │  cv_candidates  │
                                        │  hiring_status  │  ← auto-recalculated
                                        └─────────────────┘     on every stage move
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Angular 21, Bootstrap 5, Angular Signals (zoneless CD), standalone components |
| Backend | Spring Boot 3.3.5, Java 21, Spring Security (JWT cookie `access-token`) |
| Database | PostgreSQL 16, Flyway migrations (V1–V20) |
| Cache | Redis 7 (permission cache) |
| CV Extraction | Python 3.11, watchdog, PyMuPDF, pytesseract, python-docx, `@llamaindex/liteparse` |
| LLM | Ollama (llama3) |
| Observability | Logstash, Kibana (ELK stack), MDC structured logging |
| Infrastructure | Docker, Docker Compose |

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Docker Desktop | Latest | Required for the backend stack |
| Node.js | 18+ | Required for the Angular frontend |
| npm | 9+ | Comes with Node.js |
| Ollama | Latest | Required for CV extraction (LLM mode) |

---

## Step 1 — Install and Configure Ollama

Ollama runs the local LLM that extracts structured data from CV text.

### Install Ollama

**macOS**
```bash
brew install ollama
```
Or download from https://ollama.com/download

**Linux**
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

**Windows** — download the installer from https://ollama.com/download

### Start the Ollama server
```bash
ollama serve
```
Ollama listens on `http://localhost:11434`. Keep this terminal open (or run it as a background service).

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

## Step 2 — Build the Backend JAR

The Dockerfile copies a pre-built JAR (no Maven inside Docker):

```bash
cd demo-app-backend
JAVA_HOME=/opt/tools/jdk-21.0.11/Contents/Home \
MAVEN_HOME=/opt/tools/apache-maven-3.9.16 \
$MAVEN_HOME/bin/mvn package -DskipTests
```

> If your Maven/JDK paths differ, adjust the environment variables accordingly.

---

## Step 3 — Start the Backend Stack

```bash
cd demo-app-backend
docker compose up -d --build
```

This starts the following containers:

| Container | Port | Description |
|---|---|---|
| `app` | 8080 | Spring Boot REST API + Flyway migrations |
| `postgres` | 5432 | PostgreSQL 16 database |
| `redis` | 6379 | Redis 7 permission cache |
| `cv-extractor` | — | Python CV extraction watcher |
| `logstash` | — | Log ingestion (optional ELK) |

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
docker compose logs -f app          # Spring Boot API
docker compose logs -f cv-extractor # Extraction pipeline
docker compose logs -f postgres     # Database errors
```

---

## Step 4 — Start the Frontend

```bash
cd demo-app
npm install
npm start
```

The Angular dev server starts at **http://localhost:4200**.

---

## Demo Accounts

All seeded by V8 and V19 Flyway migrations:

| Role | Email | Password | Key Permissions |
|---|---|---|---|
| **Administrator** | `admin@demo.com` | `admin123` | All 18 permissions |
| **Manager** | `manager@demo.com` | `manager123` | IAM read/write, recruitment visibility, analytics |
| **Viewer** | `viewer@demo.com` | `viewer123` | Authenticated endpoints only |
| **HR** | `hr@demo.com` | `hr123` | Full recruitment pipeline (no IAM) |
| **Dev Team** | `devteam@demo.com` | `devteam123` | Hiring requests, Shared CVs inbox, interview feedback |

> The Dev Team role does **not** have `candidateSearch` — they only see candidates that HR explicitly shares with them.

---

## Step 5 — Upload a CV and See Extraction in Action

1. Log in at http://localhost:4200 as `hr@demo.com`
2. Navigate to **Documents**
3. Open or create a CV-type document category — choose the extraction mode (see below)
4. Upload a PDF or DOCX CV file
5. Watch the extractor process it:
   ```bash
   docker compose logs -f cv-extractor
   ```
6. After extraction, click **View Candidates** on the category to see the structured profile
7. The candidate's `hiring_status` will be set to **Available** automatically

---

## CV Extraction Pipeline

The extractor uses an 11-guard pipeline, a circuit breaker, and a bounded worker pool.

### Extraction Modes

Each CV category has a **"Use LLM extraction"** toggle (visible in the category form when type = CV):

| Mode | Text Extractor | LLM Called | When to Use |
|---|---|---|---|
| **LLM** (toggle ON, default) | Tesseract / PDFMiner / python-docx | Yes | Standard — full structured extraction |
| **LiteParse** (toggle OFF) | `@llamaindex/liteparse` CLI | Yes | Higher-fidelity raw text fed to the same LLM |

Both modes run through the same output guardrails.

### Processing Statuses

| Status | Meaning |
|---|---|
| `PASS` | All guards passed — confidence HIGH |
| `DEGRADED` | One or more WARNs fired — data present but flagged for review |
| `REJECTED` | At least one BLOCK — document unrecoverable, written to dead-letter |
| `ERROR` | Unhandled exception — written to dead-letter |

### Circuit Breaker (LLM resilience)

```
CLOSED ──(5 failures / 60 s)──► OPEN ──(30 s cooldown)──► HALF-OPEN
  ▲                                                              │
  └─────────────────(probe success)─────────────────────────────┘
```

While OPEN, LLM calls fast-fail immediately — no retries, no blocking threads.

---

## Project Structure

```
HR_Portal/
├── docs/                                # Platform-level documentation
│   ├── HR_PLATFORM.md                   # ★ Product backlog, workflow, gap analysis, sprint plan
│   ├── ARCHITECTURE.md                  # System architecture decisions
│   ├── DATABASE_SCHEMA.md               # Full DB schema reference (V1–V20)
│   ├── UI_FEATURES.md                   # Angular component and page reference
│   ├── JACOCO_SETUP.md                  # Code coverage gate setup
│   ├── LOGGING_MDC_ELK.md               # ELK structured logging setup
│   └── KIBANA_SETUP.md                  # Kibana dashboard setup
│
├── demo-app/                            # Angular 21 frontend
│   └── src/app/
│       ├── auth/                        # Login page, JWT interceptor, guards
│       ├── core/                        # Inactivity service, app-level config
│       ├── shared/                      # Reusable components (data-table, btn, dropdown…)
│       ├── layout/                      # Shell, header, sidebar (permission-filtered nav)
│       └── features/
│           ├── dashboard/               # Home dashboard
│           ├── documents/               # Document & category management
│           ├── cv-candidates/           # CV candidate profiles, search, hiring_status badges
│           ├── cv-shares/               # Dev Team Shared CVs inbox + impression form
│           ├── hiring-requests/         # Hiring request form, HR queue, request detail
│           ├── recruitment/             # Job postings, Kanban board, interviews
│           ├── hr-analytics/            # Recruitment funnel, time-to-hire, top skills
│           ├── knowledge/               # Knowledge graph browser
│           ├── users/                   # User management
│           ├── roles/                   # Role & permission management (18 fields)
│           ├── reports/                 # Reports
│           ├── profile/                 # User profile
│           └── settings/                # Application settings
│
├── demo-app-backend/                    # Spring Boot 3.3.5 / Java 21 backend
│   └── src/main/java/com/demo/app/
│       ├── config/                      # Security (JWT cookie), CORS, storage, Redis
│       ├── content/                     # Documents, categories, extraction status
│       ├── cv/                          # CV candidates, extraction ingest, hiring_status
│       │   ├── entity/CandidateHiringStatus.java   # AVAILABLE|IN_PROCESS|OFFERED|HIRED|REJECTED|WITHDRAWN
│       │   └── service/CvCandidateService.java
│       ├── hiring/                      # Hiring requests, cv_shares, impressions
│       │   ├── entity/CvShare.java
│       │   └── service/CvShareService.java
│       ├── recruitment/                 # Job postings, applications, interviews, feedback
│       │   └── service/CandidateHiringStatusService.java  # recalculate() on every stage move
│       ├── iam/                         # Users, roles, PERM_* permissions, JWT
│       ├── insights/                    # HR analytics, reports
│       ├── knowledge/                   # Knowledge graph
│       ├── compliance/                  # Audit and compliance
│       ├── personal/                    # Personal data management
│       └── platform/                   # Shared exceptions, error handlers
│
├── demo-app-backend/src/main/resources/
│   └── db/migration/                    # Flyway migrations V1–V20
│       ├── V17__recruitment_pipeline.sql  # job_postings, job_applications, interviews
│       ├── V18__hiring_requests_cv_sharing.sql  # hiring_requests, cv_shares
│       ├── V19__dev_team_role.sql         # Dev Team role + 6 permissions + demo user
│       └── V20__candidate_hiring_status.sql # hiring_status column on cv_candidates
│
└── cv-batch-extractor/                  # Python CV extraction microservice
    └── app/
        ├── config.py                    # All settings (pydantic-settings)
        ├── domain/cv_schema.py          # CvExtraction Pydantic model
        ├── guardrails/
        │   ├── base.py                  # GuardrailReport, PipelineContext, GuardrailPipeline
        │   ├── input/                   # 6 pre-LLM guards
        │   └── output/                  # 5 post-LLM guards
        ├── pipeline.py                  # Wires input + LLM + output → ProcessingResult
        ├── liteparse_service.py         # Calls `lit parse` CLI, falls back to parsers
        ├── llm_service.py               # Ollama client + CircuitBreaker
        ├── worker.py                    # ThreadPoolExecutor + bounded queue
        ├── dead_letter.py               # Append-only NDJSON rejection log
        ├── backend_client.py            # Notify backend + fetch extraction mode
        ├── parsers.py                   # PDF / DOCX / image raw text extraction
        └── watcher.py                   # Watchdog observer → worker.submit()
```

---

## Key Design Decisions

- **Auth:** JWT stored in an `access-token` HTTP-only cookie. **Never** use `@PreAuthorize("hasAnyRole(...)")` — it evaluates `ROLE_*` prefixes and always returns 403. Use `hasAuthority('PERM_...')` or rely on `anyRequest().authenticated()` for internal-only endpoints.
- **Permissions:** 18 boolean fields per role — 6 IAM + 12 recruitment/hiring. The frontend uses `AuthService.can(permission)` and `permGuard(permission)` as route guards.
- **Sidebar access control:** Items use a `permission` field (show if user HAS it) or a `hiddenFor` field (hide if user HAS it). Documents and Knowledge Base are hidden from Dev Team via `hiddenFor: 'cvSharesReceive'`.
- **Kanban dropdowns:** Angular signal-controlled (`openId = signal<string|null>(null)` + `@HostListener('document:click')` + `position: fixed` + `getBoundingClientRect()`). Bootstrap JS dropdowns are NOT used — Bootstrap JS is not loaded.
- **Hiring status recalculation:** `CandidateHiringStatusService.recalculate()` runs synchronously in the same transaction as every `apply()` and `moveStage()` call. Priority: `HIRED > OFFERED > IN_PROCESS > REJECTED > AVAILABLE`. `WITHDRAWN` is set manually by HR.
- **Coverage gate:** JaCoCo ≥ 95% branch coverage enforced in `pom.xml`.

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

**Backend returns 403**
- Check that permissions use `PERM_*` authority style, not `ROLE_*`
- Check `INTERNAL_API_KEY` is the same value in both `app` and `cv-extractor` sections of `docker-compose.yml`

**LLM returns invalid JSON**
- The `JsonParseGuard` strips markdown fences and finds the outermost `{...}` block before giving up
- Check `docker compose logs cv-extractor` for the raw LLM output

**LiteParse mode produces empty text**
- The service falls back to `parsers.py` automatically if the `lit` CLI fails
- Check `docker compose logs cv-extractor` for `liteparse_extractor` BLOCK messages

**Null constraint violations in PostgreSQL**
- The mapper applies `"Unknown"` fallbacks for all `@NotBlank` fields
- Check `docker compose logs postgres` for the specific column name

**Frontend cannot connect to backend**
- Confirm backend is healthy: `curl http://localhost:8080/actuator/health`
- Check CORS: `CORS_ORIGINS` must include `http://localhost:4200`

**Dev Team user sees 403 on /cv-candidates**
- Expected — Dev Team does not have `candidateSearch` permission
- Dev Team accesses candidates only via the Shared CVs inbox (`/cv-shares`)
