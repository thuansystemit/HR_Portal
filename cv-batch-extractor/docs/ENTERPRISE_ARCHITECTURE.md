# Enterprise Architecture Plan — cv-batch-extractor with LangChain

> This document supersedes and extends `LANGCHAIN_REFACTOR_PLAN.md`.
> It designs the extraction service for **horizontal scale, multi-tenancy, provider agnosticism,
> and operational maturity** — built to grow from a single Docker Compose node to a
> Kubernetes-based, globally distributed platform.

---

## Table of Contents

1. [Current Limitations Analysis](#1-current-limitations-analysis)
2. [Enterprise Design Principles](#2-enterprise-design-principles)
3. [Target Architecture Overview](#3-target-architecture-overview)
4. [Layer Deep-Dives](#4-layer-deep-dives)
   - [4.1 Ingestion & Queue Layer](#41-ingestion--queue-layer)
   - [4.2 Orchestration Layer (LangGraph)](#42-orchestration-layer-langgraph)
   - [4.3 Intelligent LLM Layer](#43-intelligent-llm-layer)
   - [4.4 Data & Event Layer](#44-data--event-layer)
   - [4.5 Plugin & Extension Layer](#45-plugin--extension-layer)
   - [4.6 Observability Layer](#46-observability-layer)
   - [4.7 Multi-Tenancy Layer](#47-multi-tenancy-layer)
5. [New Module Map](#5-new-module-map)
6. [Implementation Milestones](#6-implementation-milestones)
   - [Milestone 1 — LangChain Core](#milestone-1--langchain-core)
   - [Milestone 2 — Async Queue Processing](#milestone-2--async-queue-processing)
   - [Milestone 3 — LangGraph Orchestration](#milestone-3--langgraph-orchestration)
   - [Milestone 4 — Intelligent LLM Layer](#milestone-4--intelligent-llm-layer)
   - [Milestone 5 — Enterprise Data Layer](#milestone-5--enterprise-data-layer)
   - [Milestone 6 — Full Observability Stack](#milestone-6--full-observability-stack)
   - [Milestone 7 — Multi-Tenancy & Plugin System](#milestone-7--multi-tenancy--plugin-system)
   - [Milestone 8 — Production Infrastructure (Kubernetes)](#milestone-8--production-infrastructure-kubernetes)
7. [Dependency Map](#7-dependency-map)
8. [Configuration Hierarchy](#8-configuration-hierarchy)
9. [Capacity Planning](#9-capacity-planning)
10. [Risk Register](#10-risk-register)
11. [Decision Log](#11-decision-log)

---

## 1. Current Limitations Analysis

### Scalability Ceilings

| Concern | Current Implementation | Ceiling Hit When... |
|---|---|---|
| **Concurrency** | `ThreadPoolExecutor(max_workers=4)` in-process | Need > 4 parallel extractions |
| **Horizontal scaling** | Watchdog watches a local volume (1 container) | Need > 1 extractor pod |
| **LLM throughput** | Single Ollama endpoint, no routing | Ollama queue backs up under load |
| **LLM resilience** | Custom circuit breaker, single provider | Ollama host goes down |
| **Dead-letter** | `dead_letter.ndjson` — local file, not queryable | Need replay, audit, dashboards |
| **Backend coupling** | Synchronous HTTP `POST /cv-cands` on each extraction | Backend outage stalls extractor |
| **Guard pipeline** | Linear `GuardrailPipeline`, sequential | Independent guards waste time |
| **Observability** | Python `logging` only | Debug a DEGRADED CV in production |
| **Configuration** | Flat `pydantic-settings` env vars | Need per-tenant or per-category LLM settings |
| **Multi-tenancy** | Single category extraction mode toggle | Need isolated queues, quotas, models per org |

### What Must Not Change

The following external contracts are frozen — enterprise layers must wrap them:

- `ProcessingResult` dataclass (status, cv_data, reports, output_file)
- Backend notification payload (`documentId`, `documentCategoryId`, `jsonFile`)
- Dead-letter NDJSON format fields
- `CvExtraction` Pydantic schema (adding fields is OK; removing is not)
- File path convention: `{upload_dir}/cv/{categoryId}/{documentId}/{filename}`
- Output file naming: `cv_{first}_{last}_{docId_short}.json`

---

## 2. Enterprise Design Principles

### P1 — Decouple with Async Messaging
Every handoff between system components crosses a message queue, not a direct HTTP call.
This means each component can scale, deploy, and fail independently.

### P2 — LangGraph as the Workflow Brain
The extraction pipeline becomes a stateful graph — not a linear list of functions.
Nodes can run in parallel, checkpoint state for crash-recovery, emit streaming progress,
and pause for human review on DEGRADED results.

### P3 — Provider Agnosticism via LangChain
The codebase must never hard-code Ollama. Any LLM swap (Ollama → OpenAI → Anthropic → Claude)
changes one config value, not application code.

### P4 — Reliability by Composition, Not by Code
Retry, fallback, circuit-breaking, and rate-limiting are applied as LangChain LCEL
decorators (`.with_retry()`, `.with_fallbacks()`, `.with_rate_limit()`) — not hand-rolled.

### P5 — Structured Output Over Parsing Heuristics
Prefer `ChatModel.with_structured_output(CvExtraction)` (tool calling / JSON mode) over
prompt-engineering-dependent `PydanticOutputParser`. This eliminates the entire class of
"LLM returned invalid JSON" failures.

### P6 — Every Failure is Queryable and Replayable
Dead letters go to a PostgreSQL table. Any operator can query, filter, and replay failed
extractions through a REST API — no SSH access to read a log file.

### P7 — Observability is Non-Negotiable at Scale
Every extraction emits: structured log (JSON), OpenTelemetry span, Prometheus metric, and
(optionally) a LangSmith trace. Debugging never requires reading raw container logs.

### P8 — Extend Without Modifying
New guard types, new LLM providers, new document loaders, and new output formats are
added by registering a plugin — not by editing `pipeline.py`.

### P9 — Multi-Tenancy from Day One
Queue isolation, rate limiting, LLM model selection, and data retention policies are
per-tenant configuration — not per-category manual overrides.

### P10 — Stateless Workers, Stateful Infrastructure
Worker pods hold no local state. All state lives in Redis (queue), PostgreSQL (results,
dead-letters), and the shared volume (uploads, output JSON). Any pod can die and restart
without losing work.

---

## 3. Target Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                              INGESTION LAYER                                         │
│                                                                                      │
│  ┌─────────────────┐    ┌──────────────────┐    ┌──────────────────────────────┐   │
│  │  File Watcher   │    │  REST Trigger    │    │  Future: S3 Event / Webhook  │   │
│  │  (watchdog)     │    │  POST /extract   │    │  (pluggable ingestion)       │   │
│  └────────┬────────┘    └────────┬─────────┘    └──────────────┬───────────────┘   │
│           └────────────────────┬─┘                              │                   │
│                                ▼                                │                   │
│                    ┌───────────────────────┐                    │                   │
│                    │  Ingestion Service    │◀───────────────────┘                   │
│                    │  • Dedup (Redis SET)  │                                        │
│                    │  • Route to queue     │                                        │
│                    └──────────┬────────────┘                                        │
└───────────────────────────────┼────────────────────────────────────────────────────┘
                                │
              ┌─────────────────▼──────────────────────┐
              │         REDIS STREAMS (Broker)          │
              │                                         │
              │  ┌──────────────────────────────────┐  │
              │  │  cv:extract:high   (VIP tenants)  │  │
              │  │  cv:extract:standard (default)    │  │
              │  │  cv:extract:bulk   (batch import) │  │
              │  │  cv:events         (results out)  │  │
              │  │  cv:dead-letter    (failed items) │  │
              │  └──────────────────────────────────┘  │
              └─────────────────┬──────────────────────┘
                                │
┌───────────────────────────────▼────────────────────────────────────────────────────┐
│                         WORKER POOL (Celery)                                        │
│                                                                                     │
│  ┌──────────────────────────────────────────────────────────────────────────────┐  │
│  │   Pod A               Pod B               Pod C  (horizontally scalable)    │  │
│  │  ┌────────────┐      ┌────────────┐      ┌────────────┐                     │  │
│  │  │ Celery     │      │ Celery     │      │ Celery     │                     │  │
│  │  │ Worker     │      │ Worker     │      │ Worker     │                     │  │
│  │  │ extract_cv │      │ extract_cv │      │ extract_cv │                     │  │
│  │  └─────┬──────┘      └─────┬──────┘      └─────┬──────┘                     │  │
│  └────────┼────────────────────┼──────────────────┼──────────────────────────┘  │
└───────────┼────────────────────┼──────────────────┼────────────────────────────┘
            └────────────────────┼──────────────────┘
                                 │ LangGraph.invoke()
┌────────────────────────────────▼───────────────────────────────────────────────────┐
│                      ORCHESTRATION LAYER (LangGraph)                                │
│                                                                                     │
│  ExtractionGraph (StateGraph)                                                       │
│                                                                                     │
│  ┌──────────┐   ┌──────────┐                                                       │
│  │FileSizeG │   │MimeTypeG │  ← parallel node execution                            │
│  └────┬─────┘   └────┬─────┘                                                       │
│       └──────┬────────┘                                                             │
│              ▼                                                                      │
│       ┌─────────────────────────────┐                                              │
│       │  DocumentLoaderNode         │  ← LangChain community loaders               │
│       │  (PyMuPDF / Docx2txt /      │                                              │
│       │   UnstructuredImage /       │                                              │
│       │   LiteParseExtractor)       │                                              │
│       └──────────────┬──────────────┘                                              │
│                      ▼                                                              │
│  ┌──────────────┐   ┌──────────────┐                                               │
│  │TextLengthG   │   │TextQualityG  │  ← parallel                                   │
│  └──────┬───────┘   └──────┬───────┘                                               │
│         └────────┬──────────┘                                                       │
│                  ▼                                                                  │
│          ┌───────────────┐                                                          │
│          │ InjectionGuard│                                                          │
│          └───────┬───────┘                                                         │
│                  │  [BLOCK?] → ResultBuilder → END                                 │
│                  ▼                                                                  │
│  ┌───────────────────────────────────────────────────────────────────────────┐     │
│  │              LLM EXTRACTION NODE                                          │     │
│  │                                                                           │     │
│  │  LLMRouterNode                                                            │     │
│  │    ├── quality_score < 0.5  → PowerfulModelChain  (llama3.1 / Claude)   │     │
│  │    └── quality_score ≥ 0.5  → FastModelChain      (llama3 / Haiku)      │     │
│  │                                                                           │     │
│  │  FastModelChain:                                                          │     │
│  │    prompt | ChatOllama.with_structured_output(CvExtraction)              │     │
│  │           | .with_retry(n=3)                                             │     │
│  │           | .with_fallbacks([PowerfulModelChain])                        │     │
│  │                                                                           │     │
│  │  CircuitBreakerGuard (per-provider FSM)                                  │     │
│  └───────────────────────────────────────────────────────────────────────────┘     │
│                  │                                                                  │
│                  ▼                                                                  │
│  ┌──────────┐   ┌──────────┐   ┌───────────┐                                      │
│  │SemanticG │   │Confidence│   │ Sanitize  │  ← parallel where safe                │
│  └────┬─────┘   └────┬─────┘   └─────┬─────┘                                      │
│       └──────────────┼────────────────┘                                            │
│                      ▼                                                              │
│              ┌───────────────┐      DEGRADED?                                       │
│              │ ResultBuilder │ ──────────────▶ HumanReviewNode (optional)          │
│              └───────┬───────┘                    │                                 │
│                      │ PASS/DEGRADED              │ approved                        │
│                      ▼                            ▼                                 │
│              ┌───────────────┐           ┌────────────────┐                        │
│              │  Write JSON   │           │  Write JSON    │                        │
│              │  Emit Event   │           │  Emit Event    │                        │
│              └───────────────┘           └────────────────┘                        │
└────────────────────────────────────────────────────────────────────────────────────┘
            │                          │
            ▼                          ▼
┌─────────────────────┐   ┌────────────────────────────────────────────────────────┐
│   LLM PROVIDERS     │   │                 DATA LAYER                             │
│                     │   │                                                        │
│  Primary:           │   │  ┌──────────────┐  ┌──────────────┐  ┌────────────┐  │
│  Ollama :11434      │   │  │  PostgreSQL  │  │  Redis       │  │  VectorDB  │  │
│  (local, free)      │   │  │  cv_cands    │  │  Streams     │  │  (Chroma / │  │
│                     │   │  │  dead_letter │  │  Cache       │  │  Weaviate) │  │
│  Fallback:          │   │  │  tenants     │  │              │  │            │  │
│  OpenAI / Claude    │   │  └──────────────┘  └──────────────┘  └────────────┘  │
│  (cloud, paid)      │   └────────────────────────────────────────────────────────┘
└─────────────────────┘
            │
┌───────────▼────────────────────────────────────────────────────────────────────────┐
│                         OBSERVABILITY LAYER                                         │
│                                                                                     │
│  LangSmith (LLM traces) │ OpenTelemetry (spans) │ Prometheus (metrics) │ Loki (logs)│
└────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Layer Deep-Dives

### 4.1 Ingestion & Queue Layer

**Problem**: The current watcher + ThreadPoolExecutor cannot scale past a single container.
Adding a second container would cause duplicate processing of the same file.

**Solution**: Thin watcher → Redis Streams → Celery workers (N pods).

#### Ingestion Service

```python
# app/ingestion/service.py

import redis.asyncio as aioredis
from app.config import settings

redis = aioredis.from_url(settings.redis_url)

async def enqueue_extraction(document_id: str, category_id: str,
                              file_path: str, priority: str = "standard") -> bool:
    """
    Enqueue a CV extraction task onto the appropriate Redis Stream.
    Returns False if document_id already queued (idempotent dedup).
    """
    dedup_key = f"cv:dedup:{document_id}"
    if await redis.set(dedup_key, "1", nx=True, ex=3600):  # 1-hour dedup window
        stream = f"cv:extract:{priority}"
        await redis.xadd(stream, {
            "document_id": document_id,
            "category_id": category_id,
            "file_path": file_path,
        })
        return True
    return False  # duplicate, already queued
```

#### Watcher becomes a thin producer

```python
# app/watcher.py (after)

class CvFileHandler(FileSystemEventHandler):
    def on_created(self, event):
        if event.is_directory:
            return
        document_id, category_id = self._parse_path(event.src_path)
        priority = self._resolve_priority(category_id)

        # Non-blocking publish — watcher never waits for extraction
        asyncio.run(enqueue_extraction(document_id, category_id, event.src_path, priority))
```

#### Priority Queues

| Stream | Who uses it | Worker ratio |
|---|---|---|
| `cv:extract:high` | Enterprise/VIP tenants | Dedicated workers (always available) |
| `cv:extract:standard` | Default tenant tier | Most workers |
| `cv:extract:bulk` | Batch CV imports | Low-priority workers, throttled |

Priority routing is determined by the tenant configuration fetched from PostgreSQL at startup and cached in Redis.

#### Celery Worker

```python
# app/tasks.py

from celery import Celery
from kombu import Queue

celery_app = Celery("cv_extractor", broker=settings.redis_url)
celery_app.conf.task_queues = [
    Queue("high",     routing_key="high"),
    Queue("standard", routing_key="standard"),
    Queue("bulk",     routing_key="bulk"),
]
celery_app.conf.task_acks_late = True           # ack only after success
celery_app.conf.task_reject_on_worker_lost = True  # re-queue if pod crashes

@celery_app.task(
    bind=True,
    max_retries=3,
    default_retry_delay=60,
    acks_late=True,
)
def extract_cv(self, document_id: str, category_id: str, file_path: str):
    try:
        graph = build_extraction_graph()
        result = graph.invoke(ExtractionState(
            document_id=document_id,
            category_id=category_id,
            file_path=file_path,
        ))
        return result["final_status"]
    except Exception as exc:
        self.retry(exc=exc, countdown=60 * (2 ** self.request.retries))
```

**Scale out**: `docker compose up -d --scale cv-extractor-worker=8`
**Scale out on K8s**: HPA based on `redis_stream_length{stream="cv:extract:standard"}` metric.

---

### 4.2 Orchestration Layer (LangGraph)

**Problem**: The current `GuardrailPipeline` is a sequential Python list.
It cannot run guards in parallel, checkpoint on crash, stream progress, or pause for human review.

**Solution**: Replace `GuardrailPipeline` with a `LangGraph StateGraph`.

#### Extraction State

```python
# app/graph/state.py

from typing import TypedDict, Annotated
import operator
from app.domain.cv_schema import CvExtraction
from app.guardrails.base import GuardrailReport

class ExtractionState(TypedDict):
    # Inputs
    document_id: str
    category_id: str
    file_path: str
    tenant_id: str

    # Extraction config (fetched from tenant/category settings)
    use_liteparse: bool
    llm_tier: str            # "fast" | "powerful"
    llm_provider: str        # "ollama" | "openai" | "anthropic"

    # Document processing
    raw_text: str | None
    prompt_text: str | None  # injection-sanitized copy
    word_count: int
    quality_score: float     # 0.0–1.0 from TextQualityGuard

    # Extraction result
    cv_data: dict | None     # CvExtraction.model_dump()

    # Audit trail (Annotated[list, operator.add] = append-only accumulation)
    reports: Annotated[list[GuardrailReport], operator.add]

    # Control flow
    blocked: bool
    final_status: str | None  # PASS | DEGRADED | REJECTED | ERROR

    # Output
    output_file: str | None
    error: str | None

    # Human-in-the-loop flag
    pending_human_review: bool
```

#### Graph Construction

```python
# app/graph/extraction_graph.py

from langgraph.graph import StateGraph, END
from langgraph.checkpoint.redis import RedisCheckpointer  # state persistence
from app.graph.nodes import *
from app.graph.routing import *

def build_extraction_graph() -> StateGraph:
    graph = StateGraph(ExtractionState)

    # ── Input Nodes ────────────────────────────────────────────────────────────
    graph.add_node("validate_file",    validate_file_node)    # FileSizeGuard + MimeTypeGuard (parallel)
    graph.add_node("load_document",    load_document_node)    # LangChain loaders
    graph.add_node("validate_content", validate_content_node) # TextLengthGuard + TextQualityGuard (parallel)
    graph.add_node("scan_injection",   injection_guard_node)

    # ── LLM Node ───────────────────────────────────────────────────────────────
    graph.add_node("extract_with_llm", llm_extraction_node)

    # ── Output Nodes ───────────────────────────────────────────────────────────
    graph.add_node("validate_output",  validate_output_node)  # SemanticGuard + ConfidenceGuard (parallel)
    graph.add_node("sanitize",         sanitize_node)

    # ── Result Nodes ───────────────────────────────────────────────────────────
    graph.add_node("build_result",     build_result_node)
    graph.add_node("human_review",     human_review_node)    # interrupt point for DEGRADED
    graph.add_node("write_output",     write_output_node)
    graph.add_node("publish_event",    publish_event_node)   # Redis Streams

    # ── Entry & Edges ──────────────────────────────────────────────────────────
    graph.set_entry_point("validate_file")

    graph.add_conditional_edges("validate_file",
        route_on_blocked, {"blocked": "build_result", "ok": "load_document"})

    graph.add_conditional_edges("load_document",
        route_on_blocked, {"blocked": "build_result", "ok": "validate_content"})

    graph.add_conditional_edges("validate_content",
        route_on_blocked, {"blocked": "build_result", "ok": "scan_injection"})

    graph.add_edge("scan_injection", "extract_with_llm")

    graph.add_conditional_edges("extract_with_llm",
        route_on_blocked, {"blocked": "build_result", "ok": "validate_output"})

    graph.add_edge("validate_output", "sanitize")
    graph.add_edge("sanitize", "build_result")

    graph.add_conditional_edges("build_result",
        route_on_degraded, {
            "degraded_review": "human_review",  # pause here if human review enabled
            "write": "write_output",
        })

    graph.add_edge("human_review", "write_output")  # resumes after human approval
    graph.add_edge("write_output", "publish_event")
    graph.add_edge("publish_event", END)

    # ── Checkpointer (crash recovery) ──────────────────────────────────────────
    checkpointer = RedisCheckpointer(settings.redis_url)

    return graph.compile(
        checkpointer=checkpointer,
        interrupt_before=["human_review"],  # pause graph for manual review
    )
```

#### Parallel Guard Nodes

```python
# app/graph/nodes.py

import asyncio
from langgraph.types import Send

async def validate_file_node(state: ExtractionState) -> dict:
    """Run FileSizeGuard and MimeTypeGuard concurrently."""
    file_size_report, mime_report = await asyncio.gather(
        asyncio.to_thread(FileSizeGuard().run, state_to_ctx(state)),
        asyncio.to_thread(MimeTypeGuard().run, state_to_ctx(state)),
    )
    reports = [file_size_report, mime_report]
    blocked = any(r.status == GuardrailStatus.BLOCK for r in reports)
    return {"reports": reports, "blocked": blocked}
```

#### Checkpointing and Crash Recovery

```
Worker A crashes mid-extraction (after load_document, before extract_with_llm)
    ↓
Redis checkpoint has state: {raw_text: "...", reports: [...], blocked: false}
    ↓
Celery retries task → new worker picks it up
    ↓
LangGraph resumes from "extract_with_llm" node (skips load_document)
    ↓
Extraction completes correctly
```

#### Human-in-the-Loop for DEGRADED Results

```
DEGRADED result detected → graph pauses at "human_review" node
    ↓
Publishes event: {"type": "cv.review_required", "document_id": "..."}
    ↓
Spring Boot backend receives event → notify admin UI
    ↓
Admin reviews extracted data, approves or edits → POST /api/extractions/{id}/approve
    ↓
Backend publishes: {"type": "cv.review_approved", "document_id": "..."}
    ↓
LangGraph resumes from checkpoint → "write_output" node
```

---

### 4.3 Intelligent LLM Layer

**Problem**: All CVs route to the same Ollama `llama3` model regardless of document
quality, complexity, or tenant tier. No fallback when Ollama is down.

**Solution**: A routing layer selects the optimal model per extraction, with automatic
provider failover.

#### LLM Provider Registry

```python
# app/llm/providers.py

from langchain_ollama import ChatOllama
from langchain_openai import ChatOpenAI
from langchain_anthropic import ChatAnthropic
from langchain_core.language_models import BaseChatModel

def get_fast_llm() -> BaseChatModel:
    """Local Ollama — zero cost, low latency, used for most CVs."""
    return ChatOllama(
        base_url=settings.ollama_url,
        model=settings.fast_model,         # llama3
        temperature=0.0,
        timeout=settings.llm_timeout,
    )

def get_powerful_llm() -> BaseChatModel:
    """More capable model for low-quality or complex documents."""
    if settings.powerful_model_provider == "ollama":
        return ChatOllama(model=settings.powerful_model)  # llama3.1:70b
    elif settings.powerful_model_provider == "openai":
        return ChatOpenAI(model=settings.powerful_model)  # gpt-4o
    elif settings.powerful_model_provider == "anthropic":
        return ChatAnthropic(model=settings.powerful_model)  # claude-sonnet-4-6
    raise ValueError(f"Unknown provider: {settings.powerful_model_provider}")

def get_fallback_llm() -> BaseChatModel:
    """Cloud fallback when Ollama is down — always available."""
    return ChatAnthropic(model="claude-haiku-4-5-20251001")  # cost-efficient cloud
```

#### Structured Output — Preferred over PydanticOutputParser

```python
# app/llm/chains.py

from langchain_core.prompts import ChatPromptTemplate
from app.domain.cv_schema import CvExtraction

SYSTEM_PROMPT = """You are an expert CV parser. Extract a structured candidate
profile from the CV text. Follow the schema exactly.
Rules:
- Dates: YYYY-MM-DD or YYYY-MM-01 or YYYY-01-01
- Country: ISO 3166-1 alpha-2 (VN, US, JP...)
- Language codes: ISO 639-1 (en, vi, fr...)
- Set confidence: HIGH if fullName + (email or phone) + (work or education)
- isCurrent=true requires endDate=null
"""

_prompt = ChatPromptTemplate.from_messages([
    ("system", SYSTEM_PROMPT),
    ("human", "CV TEXT:\n{cv_text}"),
])

def build_chain(llm: BaseChatModel, use_structured_output: bool = True):
    """
    Build extraction chain. Prefer with_structured_output (tool calling)
    when the model supports it. Fall back to OutputFixingParser.
    """
    if use_structured_output:
        # Tool-calling / JSON mode — most reliable, no prompt-engineering for JSON
        structured_llm = llm.with_structured_output(
            CvExtraction,
            method="json_mode",  # or "function_calling" if model supports it
        )
        return _prompt | structured_llm
    else:
        # Fallback: prompt-based JSON with self-correction
        from langchain.output_parsers import OutputFixingParser
        from langchain_core.output_parsers import PydanticOutputParser
        parser = OutputFixingParser.from_llm(
            parser=PydanticOutputParser(pydantic_object=CvExtraction),
            llm=llm,
            max_retries=settings.output_fixing_max_retries,
        )
        return _prompt | llm | parser
```

#### Routing Logic

```python
# app/llm/router.py

from langchain_core.runnables import RunnableLambda

def build_routing_chain(tenant_config: TenantConfig):
    fast_chain = build_chain(
        get_fast_llm().with_fallbacks([get_fallback_llm()])
    ).with_retry(
        retry_if_exception_type=(httpx.ConnectError, httpx.TimeoutException),
        stop_after_attempt=settings.llm_max_retries,
    )

    powerful_chain = build_chain(
        get_powerful_llm().with_fallbacks([get_fallback_llm()])
    ).with_retry(...)

    def route(state: ExtractionState):
        if (state["quality_score"] < settings.quality_threshold_for_powerful
                or state["word_count"] < settings.min_words_for_fast
                or tenant_config.always_use_powerful):
            return powerful_chain
        return fast_chain

    return RunnableLambda(lambda x: route(x).invoke(x))
```

#### Circuit Breaker — Per Provider

```python
# app/llm/circuit_breaker.py

class ProviderCircuitBreaker:
    """One circuit breaker instance per LLM provider."""
    _instances: dict[str, CircuitBreaker] = {}

    @classmethod
    def for_provider(cls, provider: str) -> CircuitBreaker:
        if provider not in cls._instances:
            cls._instances[provider] = CircuitBreaker(
                failure_threshold=settings.cb_failure_threshold,
                window_seconds=settings.cb_window_seconds,
                cooldown_seconds=settings.cb_cooldown_seconds,
            )
        return cls._instances[provider]
```

Ollama's circuit breaker opens → routing chain picks the fallback (cloud) automatically.
Both providers are down → `CircuitOpenError` → REJECTED → dead-letter.

---

### 4.4 Data & Event Layer

#### Event Bus (Redis Streams)

Replace synchronous `backend_client.notify_candidate_ready()` with an event published
to Redis Streams. Spring Boot backend subscribes as a consumer group.

```
cv:events stream messages:
  {
    "type":        "cv.extracted",
    "document_id": "uuid",
    "category_id": "uuid",
    "status":      "PASS",
    "output_file": "cv_john_doe_abc12.json",
    "warnings":    ["confidence: MEDIUM"],
    "tenant_id":   "tenant-uuid",
    "timestamp":   "2026-05-18T10:23:01Z"
  }
```

Multiple backend services can consume the same event independently:
- Spring Boot: save to `cv_candidates` table
- Notification service: email recruiter
- Analytics service: update dashboards
- Audit service: write to audit log

#### Database Dead-Letter Queue

Replace `dead_letter.ndjson` file with a PostgreSQL table. This enables:
- SQL query to find all REJECTED documents by date range or reason
- REST API endpoint to list and replay failed extractions
- Retry count tracking
- Admin dashboard integration

```sql
-- V14__cv_dead_letter_queue.sql
CREATE TABLE cv_dead_letters (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL,
    category_id     UUID NOT NULL,
    tenant_id       UUID,
    file_path       TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('REJECTED', 'ERROR')),
    reason          TEXT,
    reports         JSONB,                -- full GuardrailReport list
    attempt_count   INT DEFAULT 1,
    last_attempt_at TIMESTAMPTZ DEFAULT NOW(),
    replayed_at     TIMESTAMPTZ,
    replay_result   VARCHAR(20),          -- PASS | DEGRADED | REJECTED | ERROR
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_dead_letters_unresolved
    ON cv_dead_letters (created_at DESC)
    WHERE replayed_at IS NULL;

CREATE INDEX idx_dead_letters_tenant
    ON cv_dead_letters (tenant_id, created_at DESC);
```

#### Replay API (Spring Boot addition)

```
POST /api/admin/dead-letters/{id}/replay
→ cv-extractor re-enqueues the document at "high" priority
→ replay_result updated after completion
```

#### Vector Store for Semantic Candidate Search

```python
# app/vectorstore/indexer.py

from langchain_community.vectorstores import Chroma
from langchain_community.embeddings import OllamaEmbeddings

_embeddings = OllamaEmbeddings(
    base_url=settings.ollama_url,
    model=settings.embedding_model,   # nomic-embed-text
)
_store = Chroma(
    collection_name="cv_candidates",
    embedding_function=_embeddings,
    persist_directory=settings.vectorstore_dir,
)

def index_candidate(document_id: str, cv_data: CvExtraction, raw_text: str):
    """Index extracted CV for semantic search."""
    _store.add_texts(
        texts=[raw_text],
        metadatas=[{
            "document_id": document_id,
            "candidate_name": cv_data.fullName or "",
            "technical_skills": ",".join(cv_data.technicalSkills),
            "experience_years": _calc_experience_years(cv_data.workExperiences),
        }],
        ids=[document_id],
    )

def search_candidates(query: str, k: int = 10) -> list[dict]:
    """Semantic similarity search across all indexed CVs."""
    docs = _store.similarity_search_with_score(query, k=k)
    return [{"document_id": d.metadata["document_id"], "score": s} for d, s in docs]
```

Spring Boot calls `GET /api/candidates/search?q=senior+python+engineer` →
cv-extractor's `/search` endpoint queries the vector store → returns ranked document IDs.

---

### 4.5 Plugin & Extension Layer

**Problem**: Adding a new guard, document format, or LLM provider requires modifying
`pipeline.py` or `parsers.py` — tight coupling, hard to test independently.

**Solution**: A registry pattern where plugins declare themselves by priority and stage.

#### Guard Plugin Protocol

```python
# app/plugins/base.py

from typing import Protocol, Literal, runtime_checkable
from app.guardrails.base import GuardrailReport, PipelineContext

@runtime_checkable
class GuardPlugin(Protocol):
    name: str
    priority: int          # lower = runs first
    stage: Literal["input", "output"]
    enabled_by_default: bool

    def run(self, ctx: PipelineContext) -> GuardrailReport: ...
```

#### Guard Registry

```python
# app/plugins/registry.py

class GuardRegistry:
    _guards: list[GuardPlugin] = []

    def register(self, guard: GuardPlugin) -> None:
        self._guards.append(guard)

    def get_pipeline(self, stage: str, tenant_config: TenantConfig) -> list[GuardPlugin]:
        guards = [
            g for g in self._guards
            if g.stage == stage
            and (g.enabled_by_default or g.name in tenant_config.enabled_guards)
        ]
        return sorted(guards, key=lambda g: g.priority)

# Global instance
registry = GuardRegistry()

# Built-in registrations (auto-loaded)
registry.register(FileSizeGuard())   # priority=10, stage="input"
registry.register(MimeTypeGuard())   # priority=20, stage="input"
registry.register(TextExtractor())   # priority=30, stage="input"
# ... etc
```

#### Document Loader Registry

```python
# app/plugins/loaders.py

from langchain_core.document_loaders import BaseLoader

class LoaderRegistry:
    _loaders: dict[str, type[BaseLoader]] = {}

    def register(self, extension: str, loader_cls: type[BaseLoader]):
        self._loaders[extension.lower()] = loader_cls

    def get_loader(self, file_path: str) -> BaseLoader:
        ext = Path(file_path).suffix.lower()
        loader_cls = self._loaders.get(ext)
        if not loader_cls:
            raise ValueError(f"No loader registered for extension: {ext}")
        return loader_cls(file_path)

loader_registry = LoaderRegistry()
loader_registry.register(".pdf",  PyMuPDFLoader)
loader_registry.register(".docx", Docx2txtLoader)
loader_registry.register(".doc",  Docx2txtLoader)
loader_registry.register(".png",  UnstructuredImageLoader)
loader_registry.register(".jpg",  UnstructuredImageLoader)
loader_registry.register(".jpeg", UnstructuredImageLoader)
loader_registry.register(".tiff", UnstructuredImageLoader)
```

#### Adding a New Document Format (Zero Code Change in Core)

```python
# A third-party extension package can add .odt support:
from app.plugins.loaders import loader_registry
from langchain_community.document_loaders import UnstructuredODTLoader

loader_registry.register(".odt", UnstructuredODTLoader)
```

---

### 4.6 Observability Layer

#### Metrics (Prometheus)

```python
# app/observability/metrics.py

from prometheus_client import Counter, Histogram, Gauge

extractions_total = Counter(
    "cv_extractions_total",
    "Total CV extractions by status and tenant",
    ["status", "tenant_id", "llm_provider"],
)

extraction_duration_seconds = Histogram(
    "cv_extraction_duration_seconds",
    "End-to-end extraction latency",
    ["llm_provider", "document_type"],
    buckets=[5, 10, 30, 60, 120, 300],
)

queue_depth = Gauge(
    "cv_queue_depth",
    "Current depth of extraction queues",
    ["queue"],
)

llm_tokens_used = Counter(
    "cv_llm_tokens_total",
    "Total tokens consumed by LLM",
    ["provider", "model", "tenant_id"],
)

circuit_breaker_state = Gauge(
    "cv_circuit_breaker_state",
    "Circuit breaker state: 0=CLOSED 1=OPEN 2=HALF_OPEN",
    ["provider"],
)
```

#### OpenTelemetry Spans

```python
# app/observability/tracing.py

from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter

tracer = trace.get_tracer("cv-batch-extractor")

# In LangGraph nodes:
def llm_extraction_node(state: ExtractionState) -> dict:
    with tracer.start_as_current_span("llm_extraction") as span:
        span.set_attribute("document_id", state["document_id"])
        span.set_attribute("llm_provider", state["llm_provider"])
        span.set_attribute("quality_score", state["quality_score"])

        result = routing_chain.invoke(state)

        span.set_attribute("cv.fullName_extracted",
                           bool(result.get("fullName")))
        span.set_attribute("cv.experience_count",
                           len(result.get("workExperiences", [])))
        return {"cv_data": result}
```

#### LangSmith Integration

LangSmith traces the full chain: prompt sent → raw LLM response → parsed output.
Enabled at the SDK level via environment variables — zero code changes.

```env
LANGCHAIN_TRACING_V2=true
LANGCHAIN_API_KEY=<key>
LANGCHAIN_PROJECT=cv-batch-extractor-prod
```

LangSmith shows:
- Exact prompt (with CV text) sent to each LLM call
- Whether `OutputFixingParser` triggered and its correction prompt
- Token counts and latency per LLM call
- Parsed `CvExtraction` JSON
- Which node in the LangGraph fired

#### Structured Logging (JSON)

```python
# app/observability/logging.py

import structlog

log = structlog.get_logger()

# In extraction:
log.info("extraction.completed",
    document_id=state["document_id"],
    tenant_id=state["tenant_id"],
    status=state["final_status"],
    duration_ms=elapsed,
    llm_provider=state["llm_provider"],
    guard_warnings=[r.reason for r in state["reports"] if r.status == "WARN"],
)
```

JSON logs are shipped to Loki / Elasticsearch for query and alerting.

---

### 4.7 Multi-Tenancy Layer

#### Tenant Configuration Table

```sql
-- V15__tenant_config.sql
CREATE TABLE tenant_configs (
    tenant_id           UUID PRIMARY KEY,
    name                TEXT NOT NULL,
    queue_priority      VARCHAR(20) DEFAULT 'standard',  -- high | standard | bulk
    max_extractions_per_hour INT DEFAULT 100,
    llm_tier            VARCHAR(20) DEFAULT 'fast',      -- fast | powerful
    llm_provider        VARCHAR(20) DEFAULT 'ollama',    -- ollama | openai | anthropic
    llm_model           TEXT,                            -- override model name
    enabled_guards      TEXT[],                          -- null = all defaults
    human_review_on_degraded BOOLEAN DEFAULT FALSE,
    vector_indexing_enabled  BOOLEAN DEFAULT TRUE,
    data_retention_days INT DEFAULT 365,
    created_at          TIMESTAMPTZ DEFAULT NOW()
);
```

#### Tenant Config Cache

```python
# app/tenancy/config.py

@dataclass
class TenantConfig:
    tenant_id: str
    queue_priority: str
    llm_tier: str
    llm_provider: str
    llm_model: str | None
    human_review_on_degraded: bool
    enabled_guards: list[str] | None  # None = all defaults
    max_extractions_per_hour: int
    vector_indexing_enabled: bool

class TenantConfigService:
    def __init__(self, redis_client, db_client):
        self._redis = redis_client
        self._db = db_client

    async def get(self, tenant_id: str) -> TenantConfig:
        """Fetch config with 5-minute Redis cache."""
        cache_key = f"tenant:config:{tenant_id}"
        cached = await self._redis.get(cache_key)
        if cached:
            return TenantConfig(**json.loads(cached))
        config = await self._db.fetch_tenant_config(tenant_id)
        await self._redis.setex(cache_key, 300, config.model_dump_json())
        return config
```

#### Per-Tenant Rate Limiting

```python
# In ingestion service:
async def check_rate_limit(tenant_id: str, config: TenantConfig) -> bool:
    key = f"ratelimit:{tenant_id}:{int(time.time() // 3600)}"  # hourly bucket
    count = await redis.incr(key)
    await redis.expire(key, 3600)
    if count > config.max_extractions_per_hour:
        log.warning("rate_limit.exceeded", tenant_id=tenant_id, count=count)
        return False
    return True
```

---

## 5. New Module Map

```
cv-batch-extractor/
├── main.py                          # entry: start Celery + watcher
│
├── app/
│   ├── config.py                    # EXTENDED with all new settings
│   │
│   ├── domain/
│   │   └── cv_schema.py             # MODIFIED: model_config extra="ignore"
│   │
│   ├── graph/                       # NEW: LangGraph workflow
│   │   ├── __init__.py
│   │   ├── state.py                 # ExtractionState TypedDict
│   │   ├── extraction_graph.py      # StateGraph construction
│   │   ├── nodes.py                 # All graph node functions
│   │   └── routing.py               # Conditional edge functions
│   │
│   ├── llm/                         # NEW: LangChain LLM layer
│   │   ├── __init__.py
│   │   ├── providers.py             # get_fast_llm(), get_powerful_llm(), get_fallback_llm()
│   │   ├── chains.py                # build_chain() with structured output
│   │   ├── router.py                # Routing chain (fast vs powerful)
│   │   ├── circuit_breaker.py       # Per-provider CircuitBreaker (extracted from llm_service.py)
│   │   └── prompt.py                # ChatPromptTemplate definitions
│   │
│   ├── ingestion/                   # NEW: queue publishing
│   │   ├── __init__.py
│   │   └── service.py               # enqueue_extraction() + dedup
│   │
│   ├── tasks.py                     # NEW: Celery task definitions
│   │
│   ├── plugins/                     # NEW: extensibility layer
│   │   ├── __init__.py
│   │   ├── base.py                  # GuardPlugin Protocol, LoaderPlugin Protocol
│   │   └── registry.py              # GuardRegistry, LoaderRegistry singletons
│   │
│   ├── tenancy/                     # NEW: multi-tenancy
│   │   ├── __init__.py
│   │   └── config.py                # TenantConfig + TenantConfigService
│   │
│   ├── document_loader.py           # NEW: LangChain-based loaders (replaces parsers.py)
│   │
│   ├── vectorstore/                 # NEW: semantic search
│   │   ├── __init__.py
│   │   └── indexer.py               # index_candidate(), search_candidates()
│   │
│   ├── observability/               # NEW: metrics, tracing, logging
│   │   ├── __init__.py
│   │   ├── metrics.py               # Prometheus counters/histograms
│   │   ├── tracing.py               # OpenTelemetry setup
│   │   └── logging.py               # structlog setup
│   │
│   ├── guardrails/                  # PARTIALLY MODIFIED
│   │   ├── base.py                  # UNCHANGED
│   │   ├── input/
│   │   │   ├── file_size.py         # UNCHANGED
│   │   │   ├── mime_type.py         # UNCHANGED
│   │   │   ├── text_extractor.py    # MODIFIED: use document_loader
│   │   │   ├── liteparse_extractor.py # MODIFIED: update fallback import
│   │   │   ├── text_length.py       # UNCHANGED
│   │   │   ├── text_quality.py      # UNCHANGED
│   │   │   └── injection.py         # UNCHANGED
│   │   └── output/
│   │       ├── json_parse.py        # DELETED (PydanticOutputParser takes over)
│   │       ├── schema.py            # DELETED (PydanticOutputParser takes over)
│   │       ├── semantic.py          # UNCHANGED
│   │       ├── confidence.py        # UNCHANGED
│   │       └── sanitize.py          # UNCHANGED
│   │
│   ├── backend_client.py            # SIMPLIFIED: only get_extraction_mode() remains
│   │                                #   (notification replaced by Redis Streams event)
│   ├── dead_letter.py               # REPLACED by DB-backed dead-letter service
│   ├── watcher.py                   # MODIFIED: thin producer → enqueue_extraction()
│   └── worker.py                    # DELETED: Celery replaces ThreadPoolExecutor
│
│   # DELETED FILES
│   # app/llm_service.py             → split into app/llm/ package
│   # app/parsers.py                 → replaced by app/document_loader.py
│   # app/pipeline.py                → replaced by app/graph/extraction_graph.py
│
├── docs/
│   ├── ARCHITECTURE.md              # UPDATE
│   ├── DOMAIN_MODEL.md              # UPDATE
│   ├── GUARDRAILS_SPEC.md           # UPDATE (remove 2 guard specs)
│   ├── LANGCHAIN_REFACTOR_PLAN.md   # SUPERSEDED by this document
│   └── ENTERPRISE_ARCHITECTURE.md   # THIS FILE
│
├── migrations/                      # NEW: database migrations for this service
│   ├── V14__cv_dead_letter_queue.sql
│   └── V15__tenant_configs.sql
│
├── Dockerfile                       # MODIFIED: install new deps, expose metrics port
├── docker-compose.yml               # EXTENDED: add worker replicas, Flower, Prometheus
└── requirements.txt                 # EXTENDED: LangChain + Celery + structlog + otel
```

---

## 6. Implementation Milestones

Each milestone is independently deployable and testable. Later milestones build on earlier ones.

---

### Milestone 1 — LangChain Core

**Goal**: Replace raw HTTP + custom JSON parsing with LangChain primitives.
**Prerequisite**: Nothing — this is the starting point.
**From**: `LANGCHAIN_REFACTOR_PLAN.md` phases 1–8 (execute those phases fully first).

Deliverables:
- `app/llm/providers.py` — `OllamaLLM` and `ChatOllama` wrappers
- `app/llm/chains.py` — extraction chain with `with_structured_output` + fallback `OutputFixingParser`
- `app/llm/prompt.py` — `ChatPromptTemplate` (replaces `cv_prompt.txt`)
- `app/document_loader.py` — LangChain community loaders
- `app/domain/cv_schema.py` — add `model_config = ConfigDict(extra="ignore")`
- Delete: `app/llm_service.py`, `app/parsers.py`, `app/guardrails/output/json_parse.py`,
  `app/guardrails/output/schema.py`, `cv_prompt.txt`

**Verification**:
```bash
python -c "
from app.llm.chains import build_chain
from app.llm.providers import get_fast_llm
chain = build_chain(get_fast_llm())
result = chain.invoke({'cv_text': open('tests/fixtures/sample_cv.txt').read()})
print(result.fullName, result.confidenceOverall)
"
```

---

### Milestone 2 — Async Queue Processing

**Goal**: Replace in-process `ThreadPoolExecutor` with Celery + Redis Streams.
Enable horizontal scaling by adding worker containers.

**Prerequisite**: Milestone 1 complete. Redis already in the stack.

Deliverables:
- `app/ingestion/service.py` — `enqueue_extraction()` with Redis dedup
- `app/tasks.py` — Celery `extract_cv` task
- `app/watcher.py` — thin producer (replaces `WorkerPool.submit()`)
- `app/worker.py` — DELETED (Celery replaces `ThreadPoolExecutor`)
- `docker-compose.yml` — add `cv-extractor-worker` service, Flower monitoring UI
- `requirements.txt` — add `celery[redis]`, `redis`

**New `docker-compose.yml` services**:

```yaml
cv-extractor-worker:
  build: .
  command: celery -A app.tasks worker -Q high,standard -c 4
  volumes:
    - uploads:/app/uploads
    - output:/app/output
  environment:
    - REDIS_URL=redis://redis:6379/0
  deploy:
    replicas: 2          # scale: docker compose up --scale cv-extractor-worker=N

flower:
  image: mher/flower
  command: celery flower --broker=redis://redis:6379/0
  ports:
    - "5555:5555"        # Celery monitoring dashboard
```

**Scale test**:
```bash
docker compose up --scale cv-extractor-worker=4 -d
# Upload 20 CVs simultaneously
# Verify: all processed, no duplicates, Flower shows correct worker count
```

---

### Milestone 3 — LangGraph Orchestration

**Goal**: Replace `GuardrailPipeline` linear list with `LangGraph StateGraph`.
Gain: parallel guards, crash recovery, streaming, human-in-the-loop.

**Prerequisite**: Milestone 2 complete.

Deliverables:
- `app/graph/state.py` — `ExtractionState` TypedDict
- `app/graph/nodes.py` — all graph node functions (including async parallel nodes)
- `app/graph/routing.py` — conditional edge functions
- `app/graph/extraction_graph.py` — `build_extraction_graph()`
- `app/tasks.py` — update `extract_cv` task to call `graph.invoke()`
- `requirements.txt` — add `langgraph`, `langgraph-checkpoint-redis`

**Crash recovery test**:
```bash
# Start extraction, kill worker pod mid-graph
docker kill cv-extractor-worker-1
# Restart worker
docker compose up cv-extractor-worker
# Verify: extraction resumes from last checkpoint, not from scratch
```

**Streaming progress** (optional, requires frontend integration):
```python
for event in graph.stream(state, stream_mode="values"):
    # event is partial ExtractionState after each node
    await publish_progress(document_id, event)
```

---

### Milestone 4 — Intelligent LLM Layer

**Goal**: Add multi-model routing (fast vs powerful), cloud fallback provider,
and structured output via tool calling.

**Prerequisite**: Milestone 3 complete.

Deliverables:
- `app/llm/router.py` — `build_routing_chain()` with `RunnableBranch`
- `app/llm/circuit_breaker.py` — per-provider `ProviderCircuitBreaker`
- `app/config.py` — add `fast_model`, `powerful_model`, `powerful_model_provider`,
  `quality_threshold_for_powerful`, `fallback_provider`, `fallback_model`
- `requirements.txt` — add `langchain-openai`, `langchain-anthropic` (optional at runtime)

**Routing configuration example**:
```env
FAST_MODEL=llama3
POWERFUL_MODEL=llama3.1:70b
POWERFUL_MODEL_PROVIDER=ollama
QUALITY_THRESHOLD_FOR_POWERFUL=0.5
FALLBACK_PROVIDER=anthropic
FALLBACK_MODEL=claude-haiku-4-5-20251001
ANTHROPIC_API_KEY=<key>
```

**Provider failover test**:
```bash
# Stop Ollama
ollama stop
# Upload a CV — should fall back to cloud provider automatically
# Verify: extraction succeeds, log shows "provider: anthropic"
```

---

### Milestone 5 — Enterprise Data Layer

**Goal**: Replace file-based dead-letter and synchronous backend HTTP
with a queryable dead-letter table and an event bus.

**Prerequisite**: Milestone 2 complete (Celery + Redis in place).

Deliverables:
- `migrations/V14__cv_dead_letter_queue.sql`
- `app/dead_letter_service.py` — replaces `app/dead_letter.py` (writes to DB)
- `app/events/publisher.py` — publishes to `cv:events` Redis Stream
- `app/backend_client.py` — remove `notify_candidate_ready()`; keep only `get_extraction_mode()`
- Spring Boot addition: Redis Streams consumer group for `cv:events`
- Spring Boot addition: `POST /api/admin/dead-letters/{id}/replay` endpoint
- `app/vectorstore/indexer.py` — index extracted CVs
- `requirements.txt` — add `asyncpg` or `psycopg[async]` for async DB, `chromadb`

**Dead-letter replay test**:
```bash
curl -X POST http://localhost:8080/api/admin/dead-letters/{id}/replay
# Verify: document re-appears in queue, processes successfully, replay_result updated
```

---

### Milestone 6 — Full Observability Stack

**Goal**: Every extraction emits structured logs, Prometheus metrics, OpenTelemetry spans,
and optional LangSmith traces.

**Prerequisite**: Milestone 3 complete.

Deliverables:
- `app/observability/metrics.py` — Prometheus counters, histograms, gauges
- `app/observability/tracing.py` — OpenTelemetry tracer setup
- `app/observability/logging.py` — `structlog` JSON formatter
- `docker-compose.yml` — add Prometheus + Grafana + OTLP collector
- Grafana dashboard: extraction rate, p95 latency, queue depth, error rate per tenant
- `requirements.txt` — add `prometheus-client`, `opentelemetry-sdk`, `opentelemetry-exporter-otlp`, `structlog`

**Alert rules**:
```yaml
# prometheus/alerts.yml
groups:
  - name: cv_extractor
    rules:
      - alert: ExtractionErrorRateHigh
        expr: rate(cv_extractions_total{status="ERROR"}[5m]) > 0.1
        for: 2m
        labels:
          severity: warning

      - alert: QueueDepthHigh
        expr: cv_queue_depth{queue="standard"} > 500
        for: 5m
        labels:
          severity: critical

      - alert: CircuitBreakerOpen
        expr: cv_circuit_breaker_state == 1
        for: 30s
        labels:
          severity: warning
```

---

### Milestone 7 — Multi-Tenancy & Plugin System

**Goal**: Each tenant gets isolated queues, rate limiting, LLM model selection,
and per-tenant guard configuration.

**Prerequisite**: Milestone 5 complete.

Deliverables:
- `migrations/V15__tenant_configs.sql`
- `app/tenancy/config.py` — `TenantConfig` + `TenantConfigService` with Redis cache
- `app/plugins/registry.py` — `GuardRegistry` + `LoaderRegistry`
- `app/plugins/base.py` — `GuardPlugin` Protocol
- `app/graph/extraction_graph.py` — update to pass `tenant_config` to routing chain
- `app/ingestion/service.py` — add per-tenant rate limiting

**Tenant onboarding**:
```sql
INSERT INTO tenant_configs (tenant_id, name, queue_priority, llm_tier, llm_provider)
VALUES ('acme-corp-uuid', 'ACME Corp', 'high', 'powerful', 'ollama');
```

---

### Milestone 8 — Production Infrastructure (Kubernetes)

**Goal**: Deploy the entire stack on Kubernetes with auto-scaling,
secrets management, and zero-downtime deployments.

**Prerequisite**: All previous milestones complete.

Deliverables:
- `k8s/` directory with manifests:
  - `cv-extractor-worker-deployment.yaml` — worker pods
  - `cv-extractor-watcher-deployment.yaml` — single watcher pod
  - `hpa.yaml` — Horizontal Pod Autoscaler based on queue depth
  - `service.yaml` — internal ClusterIP service
  - `configmap.yaml` — non-secret configuration
  - `external-secret.yaml` — pull secrets from Vault / AWS Secrets Manager

**Auto-scaling rule**:
```yaml
# hpa.yaml
spec:
  scaleTargetRef:
    name: cv-extractor-worker
  minReplicas: 2
  maxReplicas: 20
  metrics:
    - type: External
      external:
        metric:
          name: redis_stream_length
          selector:
            matchLabels:
              stream: cv:extract:standard
        target:
          type: AverageValue
          averageValue: "50"  # scale up when >50 messages per replica
```

**Zero-downtime deployment**:
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 0
    maxSurge: 1
```

---

## 7. Dependency Map

```
Milestone 1 (LangChain Core)
    │
    ├─── Milestone 2 (Celery Queue)
    │         │
    │         ├─── Milestone 3 (LangGraph)
    │         │         │
    │         │         ├─── Milestone 4 (Multi-LLM)  [can parallel with M5]
    │         │         │
    │         │         └─── Milestone 6 (Observability)  [can start after M3]
    │         │
    │         └─── Milestone 5 (Data Layer)
    │                   │
    │                   └─── Milestone 7 (Multi-Tenancy)
    │                             │
    │                             └─── Milestone 8 (Kubernetes)
    │
    └─── Milestones 4, 5, 6 can proceed in parallel after M2
```

---

## 8. Configuration Hierarchy

Settings are resolved in this order (later overrides earlier):

```
1. Default values in Settings class
2. .env file (local development)
3. Environment variables (Docker / K8s)
4. Tenant config (from DB, cached in Redis)       ← per-org override
5. Category config (from backend API)             ← per-category override
6. Document-level flags (embedded in queue message) ← one-off override
```

### Full `config.py` (target state)

```python
class Settings(BaseSettings):
    # Infrastructure
    redis_url: str = "redis://redis:6379/0"
    database_url: str = "postgresql+asyncpg://user:pass@postgres:5432/cvdb"
    upload_dir: str = "/app/uploads"
    output_dir: str = "/app/output"
    vectorstore_dir: str = "/app/vectorstore"

    # Backend API
    backend_url: str = "http://backend:8080"
    backend_timeout: int = 30
    internal_api_key: str = ""

    # LLM — Fast (local, free)
    ollama_url: str = "http://host.docker.internal:11434"
    fast_model: str = "llama3"
    fast_model_provider: str = "ollama"

    # LLM — Powerful (for low-quality docs)
    powerful_model: str = "llama3.1:70b"
    powerful_model_provider: str = "ollama"   # or "openai" | "anthropic"
    quality_threshold_for_powerful: float = 0.5

    # LLM — Cloud Fallback (when Ollama is down)
    fallback_provider: str = "anthropic"
    fallback_model: str = "claude-haiku-4-5-20251001"

    # Embedding model (for vector store)
    embedding_model: str = "nomic-embed-text"
    vector_indexing_enabled: bool = False

    # LLM call settings
    llm_timeout: int = 300
    llm_max_retries: int = 3
    llm_retry_delay: float = 2.0
    output_fixing_max_retries: int = 3
    output_fixing_enabled: bool = True

    # Guard thresholds
    max_file_size_mb: float = 20.0
    max_text_chars: int = 40_000
    min_text_chars: int = 50
    min_word_count: int = 30
    min_printable_ratio: float = 0.85

    # Circuit breaker (per provider)
    cb_failure_threshold: int = 5
    cb_window_seconds: int = 60
    cb_cooldown_seconds: int = 30

    # Celery / Queue
    celery_broker: str = "redis://redis:6379/1"
    celery_backend: str = "redis://redis:6379/2"
    celery_worker_concurrency: int = 4

    # Output sanitize
    sanitize_max_full_name: int = 200
    sanitize_max_email: int = 320
    sanitize_max_summary: int = 5_000
    sanitize_max_list_item: int = 1_000
    sanitize_max_default: int = 500

    # Observability
    langsmith_enabled: bool = False
    langsmith_project: str = "cv-batch-extractor"
    otel_exporter_endpoint: str = "http://otel-collector:4317"
    metrics_port: int = 9090

    class Config:
        env_file = ".env"
```

---

## 9. Capacity Planning

### Throughput Targets

| Scale | CVs/hour | Worker pods | LLM setup | Notes |
|---|---|---|---|---|
| **Startup** | < 100 | 1 pod × 4 threads | 1× Ollama (llama3) | Current target |
| **Growth** | 500 | 2 pods × 4 workers | 1× Ollama (llama3) + cloud fallback | Milestone 2 |
| **Scale** | 2 000 | 4 pods × 4 workers | 2× Ollama + cloud | Milestone 4 + HPA |
| **Enterprise** | 10 000 | HPA 2–20 pods | Ollama cluster + cloud | Milestone 8 |

### LLM Bottleneck

Ollama `llama3` on a single GPU: ~30-60s per CV depending on length.
At 60s per extraction with 4 concurrent workers: **240 CVs/hour per pod**.

To exceed 1 000 CVs/hour:
1. Run 5+ worker pods (HPA on queue depth).
2. Add a second Ollama instance + load-balance at `app/llm/providers.py`.
3. Shift high-volume tenants to `bulk` queue, processed off-peak.

### Memory Sizing

| Component | Memory | Notes |
|---|---|---|
| Celery worker pod | 512 MB–1 GB | Per worker × concurrency |
| LangGraph checkpoints | Redis: ~50 KB/extraction | Cleaned up after completion |
| Vector store (Chroma) | 1 GB/100k CVs | Grows with candidate count |
| Dead-letter table | PostgreSQL: ~5 KB/row | Index on `(status, created_at)` |

---

## 10. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| LangGraph adds latency from checkpoint writes | Medium | Low | Checkpoint only on slow nodes (LLM); skip for fast guards |
| Celery message loss on Redis restart | Low | High | `task_acks_late=True` + `task_reject_on_worker_lost=True`; use Redis persistence (AOF) |
| Cloud LLM fallback sends CV text to third party | Medium | High | Default fallback off; require explicit opt-in per tenant; document in GDPR policy |
| `with_structured_output` not supported by all Ollama models | Medium | Medium | Detect at startup; fall back to `OutputFixingParser` automatically |
| LangGraph graph version incompatibility on upgrade | Low | Medium | Pin `langgraph` to minor version; integration test before upgrade |
| Redis Streams consumer group lag (slow consumers) | Low | Medium | Monitor `cv_queue_depth` metric; alert + auto-scale |
| Vector store disk growth unbounded | Low | Medium | Implement document retention policy aligned to `data_retention_days` per tenant |
| LangSmith leaks CV PII to cloud | Low | High | `langsmith_enabled=false` by default; allow only with explicit tenant consent |
| Multi-provider costs unpredictable | Medium | Medium | LangSmith tracks token counts per tenant; alert on spend threshold |
| Celery task duplication on retry | Low | High | Idempotency: check Redis for `cv:processing:{document_id}` before running graph |

---

## 11. Decision Log

| Decision | Rationale | Alternative Considered | Rejected Because |
|---|---|---|---|
| **Celery over asyncio queue** | Redis already in stack; Celery gives monitoring (Flower), task history, priority queues out of the box | Pure `asyncio.Queue` | No persistence — pod crash loses in-flight work |
| **LangGraph over custom pipeline** | State persistence, parallel nodes, human-in-the-loop, streaming — all free with LangGraph | Keep `GuardrailPipeline` | Cannot checkpoint or run nodes in parallel |
| **`with_structured_output` preferred** | Tool calling produces valid JSON natively — removes entire parse failure class | `PydanticOutputParser` only | Parser depends on LLM following prompt instructions; fragile for weaker models |
| **`OutputFixingParser` as fallback** | Allows weaker models that don't support tool calling to self-correct | Hard REJECTED on bad JSON | Lost extraction = bad UX; self-correction recovers most cases |
| **Per-provider circuit breaker** | Ollama failure should not block cloud fallback path | Single global circuit breaker | Global breaker would close the cloud path when Ollama fails |
| **Redis Streams over Kafka** | Redis already present; Kafka adds operational overhead | Apache Kafka | Over-engineered for current scale; revisit if > 50k events/day |
| **PostgreSQL dead-letter** | SQL queries, admin API, replay built on existing infrastructure | Keep NDJSON file | Cannot query, paginate, or integrate with admin UI |
| **Chroma for vector store** | Embeds in-process, no extra service needed, LangChain native | Pinecone, Weaviate, pgvector | Pinecone/Weaviate add external service; pgvector needs schema changes |
| **structlog for logging** | JSON output, context binding, compatible with Loki | Standard `logging` | Standard logging requires manual JSON formatter; structlog context binding reduces per-call verbosity |
| **Guard plugin registry** | New formats/guards added without touching core pipeline | Hardcoded guard list | Hardcoded list requires PR to add new document type; plugin registry allows extension packages |

---

*Document version: 1.0 — May 2026*
*Branch: langchain*
*Author: Engineering Team*
*Supersedes: `LANGCHAIN_REFACTOR_PLAN.md` (basic refactor plan — still valid for Milestone 1)*
