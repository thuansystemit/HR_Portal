# CV Batch Extractor — Domain Model & Implementation Plan

---

## Part 1: Domain Model

### 1.1 CvExtraction (Pydantic)

Single source of truth for the CV schema. Replaces the JSON schema comment in `cv_prompt.txt` and the loose dict passing in `extractor.py`.

```python
# app/domain/cv_schema.py

from __future__ import annotations
from typing import Literal
from pydantic import BaseModel, Field


class WorkExperience(BaseModel):
    company: str
    title: str
    startDate: str | None = None
    startDatePrecision: Literal["YEAR", "MONTH"] | None = None
    endDate: str | None = None
    isCurrent: bool = False
    location: str | None = None
    isRemote: bool | None = None
    responsibilities: list[str] = Field(default_factory=list)
    achievements: list[str] = Field(default_factory=list)
    technologies: list[str] = Field(default_factory=list)


class Education(BaseModel):
    institution: str
    degree: str | None = None
    fieldOfStudy: str | None = None
    startYear: int | None = None
    endYear: int | None = None
    gpa: float | None = None
    honors: str | None = None


class Language(BaseModel):
    language: str
    proficiency: Literal["Native", "Fluent", "Professional", "Conversational", "Basic"]


class Certification(BaseModel):
    name: str
    issuer: str | None = None
    issuedDate: str | None = None
    expiryDate: str | None = None
    credentialId: str | None = None


class Project(BaseModel):
    name: str
    description: str | None = None
    technologies: list[str] = Field(default_factory=list)
    url: str | None = None


class Publication(BaseModel):
    title: str
    journal: str | None = None
    year: int | None = None
    url: str | None = None


class CvExtraction(BaseModel):
    fullName: str | None = None
    email: str | None = None
    phone: str | None = None
    city: str | None = None
    country: str | None = None
    linkedinUrl: str | None = None
    githubUrl: str | None = None
    portfolioUrl: str | None = None
    summary: str | None = None
    toolsAndFrameworks: list[str] = Field(default_factory=list)
    softSkills: list[str] = Field(default_factory=list)
    technicalSkills: list[str] = Field(default_factory=list)
    projects: list[Project] = Field(default_factory=list)
    publications: list[Publication] = Field(default_factory=list)
    workExperiences: list[WorkExperience] = Field(default_factory=list)
    educations: list[Education] = Field(default_factory=list)
    languages: list[Language] = Field(default_factory=list)
    certifications: list[Certification] = Field(default_factory=list)
    rawLanguage: str | None = None
    confidenceOverall: Literal["HIGH", "MEDIUM", "LOW"] | None = None
    lowConfidenceFields: list[str] = Field(default_factory=list)
    missingFields: list[str] = Field(default_factory=list)
```

---

### 1.2 Guardrail Types

```python
# app/guardrails/base.py

from __future__ import annotations
from dataclasses import dataclass, field
from typing import Literal, Protocol
from app.domain.cv_schema import CvExtraction


@dataclass
class GuardrailReport:
    guard: str                                   # e.g. "file_size", "schema"
    status: Literal["PASS", "WARN", "BLOCK"]
    reason: str | None = None
    metadata: dict = field(default_factory=dict)


@dataclass
class PipelineContext:
    document_id: str
    category_id: str
    file_path: str
    raw_text: str | None = None           # set by TextExtractor
    prompt_text: str | None = None        # set by InjectionGuard (sanitized copy)
    llm_raw: str | None = None            # set by LLMService
    raw_dict: dict | None = None          # set by JsonParseGuard
    cv_data: CvExtraction | None = None   # set by SchemaGuard
    reports: list[GuardrailReport] = field(default_factory=list)


class Guard(Protocol):
    def run(self, ctx: PipelineContext) -> GuardrailReport:
        ...


class GuardrailPipeline:
    def __init__(self, guards: list[Guard]) -> None:
        self._guards = guards

    def run(self, ctx: PipelineContext) -> list[GuardrailReport]:
        for guard in self._guards:
            report = guard.run(ctx)
            ctx.reports.append(report)
            if report.status == "BLOCK":
                break          # short-circuit; skip remaining guards
        return ctx.reports
```

---

### 1.3 ProcessingResult

```python
# app/pipeline.py (excerpt)

from __future__ import annotations
from dataclasses import dataclass, field
from typing import Literal
from app.domain.cv_schema import CvExtraction
from app.guardrails.base import GuardrailReport


@dataclass
class ProcessingResult:
    document_id: str
    category_id: str
    status: Literal["PASS", "DEGRADED", "REJECTED", "ERROR"]
    cv_data: CvExtraction | None
    output_file: str | None                      # None on REJECTED / ERROR
    reports: list[GuardrailReport] = field(default_factory=list)
    error: str | None = None                     # populated on ERROR only

    @property
    def warnings(self) -> list[str]:
        return [r.reason for r in self.reports if r.status == "WARN" and r.reason]
```

---

### 1.4 Settings Additions

New fields to add to `app/config.py`:

```python
# Guard thresholds
max_file_size_mb: float = 20.0
max_text_chars: int = 40_000
min_text_chars: int = 50
min_word_count: int = 30
min_printable_ratio: float = 0.85

# Worker pool
worker_max_workers: int = 4
worker_queue_size: int = 100

# Circuit breaker
cb_failure_threshold: int = 5
cb_window_seconds: int = 60
cb_cooldown_seconds: int = 30

# Output sanitize caps (chars)
sanitize_max_full_name: int = 200
sanitize_max_email: int = 320
sanitize_max_summary: int = 5_000
sanitize_max_list_item: int = 1_000
sanitize_max_default: int = 500
```

---

## Part 2: Implementation Plan

Implement in this order — each phase is independently testable before moving to the next.

---

### Phase 1 — Foundation

**Goal:** Establish base types and domain model. No behavior changes yet.

| Step | File | Action |
|---|---|---|
| 1.1 | `app/domain/__init__.py` | Create empty |
| 1.2 | `app/domain/cv_schema.py` | Implement `CvExtraction` and all nested models as above |
| 1.3 | `app/guardrails/__init__.py` | Create empty |
| 1.4 | `app/guardrails/base.py` | Implement `GuardrailReport`, `PipelineContext`, `Guard` protocol, `GuardrailPipeline` |
| 1.5 | `app/config.py` | Add all new settings fields with defaults |

**Verify:** `python -c "from app.domain.cv_schema import CvExtraction; from app.guardrails.base import PipelineContext"` imports cleanly.

---

### Phase 2 — Input Guards

**Goal:** Pre-LLM validation layer. Test each guard in isolation with fixture files.

| Step | File | Action |
|---|---|---|
| 2.1 | `app/guardrails/input/__init__.py` | Create empty |
| 2.2 | `app/guardrails/input/file_size.py` | Implement `FileSizeGuard` |
| 2.3 | `app/guardrails/input/mime_type.py` | Implement `MimeTypeGuard` (add `python-magic` to `requirements.txt`) |
| 2.4 | `app/guardrails/input/text_extractor.py` | Implement `TextExtractor` — wraps `parsers.extract_text()` |
| 2.5 | `app/guardrails/input/text_length.py` | Implement `TextLengthGuard` with truncation |
| 2.6 | `app/guardrails/input/text_quality.py` | Implement `TextQualityGuard` |
| 2.7 | `app/guardrails/input/injection.py` | Implement `InjectionGuard` with default patterns |

**Verify:** Manually run each guard against a sample PDF with a known edge case.

---

### Phase 3 — Output Guards

**Goal:** Post-LLM validation layer. Test against fixture JSON payloads.

| Step | File | Action |
|---|---|---|
| 3.1 | `app/guardrails/output/__init__.py` | Create empty |
| 3.2 | `app/guardrails/output/json_parse.py` | Move `_extract_json` from `extractor.py` here, wrap as `JsonParseGuard` |
| 3.3 | `app/guardrails/output/schema.py` | Implement `SchemaGuard` using `CvExtraction.model_validate` |
| 3.4 | `app/guardrails/output/semantic.py` | Implement `SemanticGuard` — all rules from spec |
| 3.5 | `app/guardrails/output/confidence.py` | Implement `ConfidenceGuard` |
| 3.6 | `app/guardrails/output/sanitize.py` | Implement `SanitizeGuard` — recursive string cleaner |

**Verify:** Pass a fixture dict with known semantic errors; confirm all WARN reasons appear in reports.

---

### Phase 4 — LLM Circuit Breaker

**Goal:** Wrap the existing `llm_service.ask_llm` with a circuit breaker. External interface unchanged.

| Step | File | Action |
|---|---|---|
| 4.1 | `app/llm_service.py` | Add `CircuitBreaker` class (CLOSED/OPEN/HALF-OPEN state machine) |
| 4.2 | `app/llm_service.py` | Wrap `ask_llm` — check breaker state before each call; record result after |

**Circuit breaker is module-level singleton** — shared across all worker threads. Use a `threading.Lock` to protect state transitions.

**Verify:** Mock Ollama endpoint to return 500s; confirm breaker opens after `cb_failure_threshold` failures and fast-fails subsequent calls.

---

### Phase 5 — Pipeline Orchestrator

**Goal:** Wire both guard pipelines and LLM call into a single `Pipeline.run()` that returns `ProcessingResult`.

| Step | File | Action |
|---|---|---|
| 5.1 | `app/pipeline.py` | Implement `ProcessingResult` dataclass |
| 5.2 | `app/pipeline.py` | Implement `Pipeline.run(ctx) -> ProcessingResult` |
| 5.3 | `app/pipeline.py` | Implement `_aggregate_status(reports)` |
| 5.4 | `app/pipeline.py` | Implement JSON file writer (write only on PASS/DEGRADED) |

**`Pipeline.run` pseudocode:**
```python
def run(self, ctx: PipelineContext) -> ProcessingResult:
    try:
        input_pipeline.run(ctx)
        if not _is_blocked(ctx.reports):
            ctx.llm_raw = ask_llm(ctx.prompt_text or ctx.raw_text)
            output_pipeline.run(ctx)
        status = _aggregate_status(ctx.reports)
        output_file = _write_json(ctx) if status in ("PASS", "DEGRADED") else None
        return ProcessingResult(
            document_id=ctx.document_id,
            category_id=ctx.category_id,
            status=status,
            cv_data=ctx.cv_data,
            output_file=output_file,
            reports=ctx.reports,
        )
    except Exception as exc:
        logger.exception("Unhandled error for document %s", ctx.document_id)
        return ProcessingResult(
            document_id=ctx.document_id,
            category_id=ctx.category_id,
            status="ERROR",
            cv_data=None,
            output_file=None,
            reports=ctx.reports,
            error=str(exc),
        )
```

**Verify:** End-to-end run with a real PDF; confirm `ProcessingResult` fields are populated correctly.

---

### Phase 6 — Dead Letter & Backend Client

**Goal:** Structured persistence for failures; enriched backend notification.

| Step | File | Action |
|---|---|---|
| 6.1 | `app/dead_letter.py` | Implement `append(result: ProcessingResult, file_path: str)` — writes one NDJSON line |
| 6.2 | `app/backend_client.py` | Add `extractionStatus` and `guardrailWarnings` to `notify_candidate_ready` signature |
| 6.3 | `app/backend_client.py` | `jsonFile` becomes `None` when `status in ("REJECTED", "ERROR")` |

---

### Phase 7 — Worker Pool & Watcher Refactor

**Goal:** Non-blocking file ingestion with bounded concurrency.

| Step | File | Action |
|---|---|---|
| 7.1 | `app/worker.py` | Implement `WorkerPool` — `ThreadPoolExecutor` + `queue.Queue(maxsize=worker_queue_size)` |
| 7.2 | `app/worker.py` | `submit(document)`: if queue full → dead-letter immediately with `QUEUE_FULL` |
| 7.3 | `app/worker.py` | Each worker task: build `PipelineContext` → `Pipeline.run()` → `notify_candidate_ready()` → dead-letter if needed |
| 7.4 | `app/watcher.py` | Replace `process()` + `notify_candidate_ready()` calls with `worker_pool.submit(task)` |
| 7.5 | `main.py` | Initialise `WorkerPool` before starting the watcher; shutdown pool on `KeyboardInterrupt` |

---

### Phase 8 — Cleanup

**Goal:** Remove dead code; ensure `extractor.py` is fully replaced.

| Step | File | Action |
|---|---|---|
| 8.1 | `app/extractor.py` | Delete file (all logic now in pipeline + guards) |
| 8.2 | `requirements.txt` | Add `python-magic` |
| 8.3 | `Dockerfile` | Add `libmagic1` system package (`apt-get install -y libmagic1`) |

---

### Phase Order Summary

```
Phase 1: Foundation (domain models, base types, config)
    ↓
Phase 2: Input Guards
    ↓
Phase 3: Output Guards
    ↓
Phase 4: LLM Circuit Breaker
    ↓
Phase 5: Pipeline Orchestrator   ← first end-to-end testable milestone
    ↓
Phase 6: Dead Letter + Backend Client
    ↓
Phase 7: Worker Pool + Watcher Refactor
    ↓
Phase 8: Cleanup
```

Each phase leaves the system in a runnable state. Phases 2–4 can be developed in parallel by separate developers since they have no interdependencies beyond Phase 1 types.
