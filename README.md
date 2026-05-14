# Enterprise CV Management Platform

A full-stack document management system with automated CV extraction powered by a local LLM (Ollama). Upload CVs, extract structured candidate profiles automatically, and browse candidates through a rich Angular UI.

## Architecture

```
┌─────────────────┐     uploads file      ┌──────────────────────┐
│  Angular 21 UI  │ ──────────────────▶   │  Spring Boot Backend │
│  (port 4200)    │ ◀──────────────────── │  (port 8080)         │
└─────────────────┘     API responses     └──────────┬───────────┘
                                                      │ stores to
                                                      ▼
                                          ┌──────────────────────┐
                                          │  /app/uploads/       │
                                          │  cv/{catId}/{docId}/ │
                                          └──────────┬───────────┘
                                                      │ watched by
                                                      ▼
                                          ┌──────────────────────┐
                                          │  cv-batch-extractor  │
                                          │  (Python / watchdog) │
                                          └──────────┬───────────┘
                                                      │ calls Ollama
                                                      ▼
                                          ┌──────────────────────┐
                                          │  Ollama (llama3)     │
                                          │  (port 11434)        │
                                          └──────────────────────┘
                                                      │ saves JSON to
                                                      ▼
                                          ┌──────────────────────┐
                                          │  /app/output/        │
                                          │  cv_name_id.json     │
                                          └──────────┬───────────┘
                                                      │ POST /api/v1/cv-candidates
                                                      ▼
                                          ┌──────────────────────┐
                                          │  PostgreSQL          │
                                          │  (cv_candidates)     │
                                          └──────────────────────┘
```

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Angular 21, Bootstrap 5, Angular Signals |
| Backend | Spring Boot 3.3.5, Java 21, Spring Security (JWT) |
| Database | PostgreSQL 16, Flyway migrations |
| Cache | Redis 7 |
| CV Extraction | Python 3.11, watchdog, PyMuPDF, pytesseract, python-docx |
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
3. Create a category with type **CV** (or use an existing one)
4. Upload a PDF or DOCX CV file
5. Watch the cv-extractor logs process it:
   ```bash
   docker compose logs -f cv-extractor
   ```
6. After extraction completes, click **View Candidates** on the CV category to see the structured profile

---

## Project Structure

```
Angular_Enterprise/
├── demo-app/                   # Angular 21 frontend
│   └── src/app/
│       ├── core/               # Guards, interceptors, global services
│       ├── shared/             # Reusable UI components, pipes, utils
│       ├── layout/             # Shell, header, sidebar
│       └── features/
│           ├── documents/      # Document & category management
│           ├── cv-candidates/  # CV candidate profiles
│           ├── users/          # User management
│           ├── roles/          # Role & permission management
│           ├── reports/        # Analytics & reports
│           └── dashboard/      # Dashboard
│
├── demo-app-backend/           # Spring Boot backend
│   └── src/main/java/com/demo/app/
│       ├── config/             # Security, CORS, storage config
│       ├── content/            # Document & category domain
│       ├── cv/                 # CV candidate domain + extraction
│       ├── iam/                # Users, roles, permissions, JWT
│       ├── insights/           # Reports & analytics
│       └── platform/           # Shared exceptions, handlers
│
└── cv-batch-extractor/         # Python CV extraction service
    └── app/
        ├── config.py           # Pydantic Settings
        ├── parsers.py          # PDF / DOCX / image text extraction
        ├── llm_service.py      # Ollama HTTP client with retry
        ├── extractor.py        # Orchestration + JSON output
        ├── watcher.py          # Filesystem watcher (watchdog)
        └── backend_client.py   # Notifies backend after extraction
```

---

## CV Extraction Flow

```
Upload CV (PDF/DOCX/image)
        ↓
Backend stores file at:
  /app/uploads/cv/{categoryId}/{documentId}/{filename}
        ↓
cv-batch-extractor detects new file (watchdog)
  → Only processes files under cv/ subtree
  → Extracts text (PyMuPDF / python-docx / tesseract)
  → Calls Ollama llama3 with structured prompt
  → Saves JSON → /app/output/cv_{name}_{shortId}.json
        ↓
cv-batch-extractor calls:
  POST /api/v1/cv-candidates
  { documentId, documentCategoryId, jsonFile }
  Header: X-Internal-Api-Key
        ↓
Backend reads JSON from /app/output/
  → Parses structured CV data
  → Persists to cv_candidates + related tables
  → Updates document extractionStatus = COMPLETED
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
- The extractor tolerates this with a multi-stage JSON parser (strips markdown fences, finds outermost `{...}` block)
- If extraction still fails, check `docker compose logs cv-extractor` for the raw LLM output

**Null constraint violations in PostgreSQL**
- The mapper applies `"Unknown"` fallbacks for all `@NotBlank` fields and filters out language entries with null names
- Check `docker compose logs postgres` for the specific column

**Frontend cannot connect to backend**
- Confirm backend is healthy: `curl http://localhost:8080/actuator/health`
- Check CORS: `CORS_ORIGINS` must include `http://localhost:4200`
