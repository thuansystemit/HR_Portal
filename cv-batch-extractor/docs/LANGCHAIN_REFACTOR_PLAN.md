# LangChain Refactoring Plan — cv-batch-extractor

## Table of Contents

1. [Goals & Motivation](#1-goals--motivation)
2. [LangChain Components Selected](#2-langchain-components-selected)
3. [Architecture Comparison](#3-architecture-comparison)
4. [Dependency Changes](#4-dependency-changes)
5. [Phase-by-Phase Plan](#5-phase-by-phase-plan)
   - [Phase 1 — Dependencies & Config](#phase-1--dependencies--config)
   - [Phase 2 — LLM Layer (OllamaLLM + PromptTemplate)](#phase-2--llm-layer-ollamallm--prompttemplate)
   - [Phase 3 — Output Parser (PydanticOutputParser)](#phase-3--output-parser-pydanticoutputparser)
   - [Phase 4 — Extraction Chain (LCEL)](#phase-4--extraction-chain-lcel)
   - [Phase 5 — Document Loaders](#phase-5--document-loaders)
   - [Phase 6 — Guard Simplification](#phase-6--guard-simplification)
   - [Phase 7 — Circuit Breaker Migration](#phase-7--circuit-breaker-migration)
   - [Phase 8 — Pipeline Rewire](#phase-8--pipeline-rewire)
   - [Phase 9 — Observability (LangSmith)](#phase-9--observability-langsmith)
   - [Phase 10 — Cleanup & Verification](#phase-10--cleanup--verification)
6. [File-by-File Change Matrix](#6-file-by-file-change-matrix)
7. [Guard Responsibility After Refactor](#7-guard-responsibility-after-refactor)
8. [Testing Strategy](#8-testing-strategy)
9. [Risk Register](#9-risk-register)
10. [Migration Checklist](#10-migration-checklist)

---

## 1. Goals & Motivation

### Why Refactor to LangChain?

The current implementation manages the full LLM integration lifecycle manually:

| Concern | Current Approach | Pain Point |
|---|---|---|
| LLM call | Raw `requests.post` to Ollama `/api/generate` | Tight coupling to Ollama HTTP API; switching models or providers requires rewriting `llm_service.py` |
| Prompt management | Single flat `cv_prompt.txt` with `{{TEXT}}` substitution | No structured message roles, no easy few-shot injection, no partial variables |
| Output parsing | `JsonParseGuard` strips fences + regex; `SchemaGuard` validates | Two guards doing what a single typed parser already does |
| Retry logic | Hand-rolled loop with `time.sleep` in `llm_service.py` | Not integrated with parsing — a bad JSON response does not trigger a retry |
| Structured output | Manual `json.loads()` → Pydantic coercion | LLM can self-correct if the parser feeds the error back |
| Document loading | Custom `parsers.py` (PyMuPDF, python-docx, pytesseract) | No chunking strategy for very long CVs |
| Observability | Python `logging` only | No trace-level visibility into prompt → LLM → parsed output |

### What LangChain Buys Us

- **Provider abstraction**: switch from `OllamaLLM` to `ChatAnthropic` / `ChatOpenAI` by changing one config line.
- **`PydanticOutputParser`**: directly deserializes LLM output into `CvExtraction` — eliminates `JsonParseGuard` and `SchemaGuard` as separate guard objects.
- **`OutputFixingParser` / `RetryWithErrorOutputParser`**: feeds malformed JSON back to the LLM for self-correction; fixes entire classes of `REJECTED` outcomes without touching guardrail code.
- **LCEL (LangChain Expression Language)**: composable `prompt | llm | parser` chains with built-in `.with_retry()`, `.with_fallbacks()`, and streaming.
- **Community document loaders**: `PyMuPDFLoader`, `Docx2txtLoader`, `UnstructuredImageLoader` replace custom `parsers.py` with maintained, battle-tested code.
- **LangSmith**: zero-code tracing of every prompt + response + parsed result for debugging and quality monitoring.

### Goals

1. Replace `llm_service.py` raw HTTP with LangChain's `OllamaLLM` / `ChatOllama`.
2. Replace `cv_prompt.txt` + manual substitution with `ChatPromptTemplate`.
3. Replace `JsonParseGuard` + `SchemaGuard` with `PydanticOutputParser` wrapped in `OutputFixingParser`.
4. Replace `parsers.py` with LangChain community document loaders.
5. Wire the extraction into a single LCEL chain with `.with_retry()`.
6. Preserve all existing guards that LangChain does not cover (file size, MIME, text quality, injection, semantic, confidence, sanitize).
7. Add optional LangSmith tracing.
8. **Keep the external contract unchanged**: same `ProcessingResult`, same backend notification payload, same dead-letter format.

---

## 2. LangChain Components Selected

| LangChain Component | Package | Replaces / Augments |
|---|---|---|
| `OllamaLLM` | `langchain-ollama` | `llm_service.py` — `ask_llm()` HTTP call |
| `ChatOllama` | `langchain-ollama` | Alternative to `OllamaLLM` when structured output / tool calling is desired |
| `ChatPromptTemplate` | `langchain-core` | `cv_prompt.txt` flat template + manual `{{TEXT}}` substitution |
| `PydanticOutputParser` | `langchain-core` | `JsonParseGuard` (JSON extraction) + `SchemaGuard` (schema validation) |
| `OutputFixingParser` | `langchain-core` | LLM self-correction on malformed JSON (new capability) |
| `RetryWithErrorOutputParser` | `langchain-core` | Alternative self-correction that replays full chain with error |
| `PyMuPDFLoader` | `langchain-community` | `parsers.pdf_to_text()` |
| `Docx2txtLoader` | `langchain-community` | `parsers.docx_to_text()` |
| `UnstructuredImageLoader` | `langchain-community` | `parsers.image_to_text()` |
| `RecursiveCharacterTextSplitter` | `langchain-text-splitters` | Long-document chunking (new capability — feeds summarised chunks) |
| LCEL `|` composition | `langchain-core` | Manual pipeline orchestration in `llm_service.py` |
| `.with_retry()` | `langchain-core` | Hand-rolled retry loop in `llm_service.py` |
| `.with_fallbacks()` | `langchain-core` | Circuit breaker fallback path (new capability) |
| LangSmith tracing | `langsmith` | Observability (new capability) |

---

## 3. Architecture Comparison

### Before (Current)

```
watcher.py
    │ file event
    ▼
worker.py (ThreadPoolExecutor)
    │
    ▼
pipeline.py — Pipeline.run()
    │
    ├─── Input Pipeline (GuardrailPipeline)
    │       FileSizeGuard
    │       MimeTypeGuard
    │       TextExtractor / LiteParseTextExtractor  ← parsers.py / liteparse_service.py
    │       TextLengthGuard
    │       TextQualityGuard
    │       InjectionGuard
    │
    ├─── llm_service.ask_llm()  ← raw requests.post to Ollama
    │       CircuitBreaker (custom FSM)
    │       Retry loop (hand-rolled)
    │
    └─── Output Pipeline (GuardrailPipeline)
            JsonParseGuard     ← json.loads + regex fence stripper
            SchemaGuard        ← Pydantic coercion + soft/hard classification
            SemanticGuard
            ConfidenceGuard
            SanitizeGuard
```

### After (LangChain)

```
watcher.py  [UNCHANGED]
    │ file event
    ▼
worker.py  [UNCHANGED]
    │
    ▼
pipeline.py — Pipeline.run()  [SIMPLIFIED]
    │
    ├─── Input Pipeline (GuardrailPipeline)  [UNCHANGED]
    │       FileSizeGuard
    │       MimeTypeGuard
    │       LangChainDocumentLoader  ← NEW: replaces parsers.py / liteparse_service.py
    │       TextLengthGuard
    │       TextQualityGuard
    │       InjectionGuard
    │
    ├─── extraction_chain  ← NEW: LCEL chain
    │       ChatPromptTemplate        (replaces cv_prompt.txt substitution)
    │           │
    │       OllamaLLM                 (replaces requests.post)
    │           │ with_retry(n=3)     (replaces hand-rolled retry loop)
    │           │ with_fallbacks([])  (replaces circuit breaker fast-fail path)
    │           │
    │       OutputFixingParser        (replaces JsonParseGuard + SchemaGuard)
    │         └─ PydanticOutputParser[CvExtraction]
    │
    └─── Output Pipeline (GuardrailPipeline)  [SIMPLIFIED]
            ~~JsonParseGuard~~    ← REMOVED (handled by PydanticOutputParser)
            ~~SchemaGuard~~       ← REMOVED (handled by PydanticOutputParser)
            SemanticGuard         [UNCHANGED]
            ConfidenceGuard       [UNCHANGED]
            SanitizeGuard         [UNCHANGED]
```

### Key Differences

| | Before | After |
|---|---|---|
| LLM call | `requests.post` | `OllamaLLM.invoke()` via LCEL |
| Prompt | `str.replace("{{TEXT}}", text)` | `ChatPromptTemplate.format_messages()` |
| JSON parsing | Custom regex + `json.loads` | `PydanticOutputParser` |
| Schema validation | Manual Pydantic coercion | `PydanticOutputParser` (automatic) |
| Self-correction | None (REJECTED on bad JSON) | `OutputFixingParser` retries with error |
| Retry | `for attempt in range(n): sleep()` | `.with_retry(stop=stop_after_attempt(3))` |
| Circuit breaker | Custom FSM (`_CircuitBreaker`) | Custom FSM kept + `.with_fallbacks([dead_chain])` |
| PDF loading | `PyMuPDF` via `parsers.py` | `PyMuPDFLoader` via langchain-community |
| DOCX loading | `python-docx` via `parsers.py` | `Docx2txtLoader` via langchain-community |
| Image loading | `pytesseract` via `parsers.py` | `UnstructuredImageLoader` via langchain-community |
| Observability | `logging` only | `logging` + optional LangSmith traces |
| Guard count | 11 guards | 9 guards (2 removed) |

---

## 4. Dependency Changes

### `requirements.txt` — Additions

```text
# LangChain core
langchain>=0.3.0
langchain-core>=0.3.0

# Ollama provider
langchain-ollama>=0.2.0

# Community loaders (PDF, DOCX, images)
langchain-community>=0.3.0

# Text splitters (separate package since langchain 0.2)
langchain-text-splitters>=0.3.0

# Optional: LangSmith observability
langsmith>=0.1.0
```

### `requirements.txt` — Removals / Replacements

| Package Removed | Replaced By |
|---|---|
| `requests` (used for Ollama HTTP) | `langchain-ollama` (`OllamaLLM` handles HTTP internally) |

> **Note**: `PyMuPDF`, `python-docx`, `pytesseract`, `Pillow` can be retained as transitive dependencies used by LangChain community loaders, or removed if loaders bundle their own extraction. Verify before removing.

### `requirements.txt` — Retained

```text
watchdog          # file system events — unchanged
pydantic          # domain models — unchanged
pydantic-settings # config — unchanged
python-magic      # MIME detection (MimeTypeGuard) — unchanged
```

---

## 5. Phase-by-Phase Plan

---

### Phase 1 — Dependencies & Config

**Goal**: Install LangChain packages, extend config with LangChain-specific settings, verify import graph.

#### 1.1 Update `requirements.txt`

Add the packages listed in [Section 4](#4-dependency-changes).

#### 1.2 Extend `app/config.py`

Add the following fields to `Settings`:

```python
# LangChain / Provider
langchain_provider: str = "ollama"          # future: "openai", "anthropic"
langchain_model: str = "llama3"             # maps to ollama_model for now
langchain_temperature: float = 0.0         # deterministic extraction

# OutputFixingParser
output_fixing_max_retries: int = 3         # how many self-correction attempts
output_fixing_enabled: bool = True         # toggle if model is too weak for self-correction

# LangSmith (optional)
langsmith_enabled: bool = False
langsmith_project: str = "cv-batch-extractor"
# LANGCHAIN_API_KEY read automatically from env by LangChain SDK
```

#### 1.3 Verification

```bash
python -c "from langchain_ollama import OllamaLLM; print('OK')"
python -c "from langchain_core.output_parsers import PydanticOutputParser; print('OK')"
python -c "from langchain_community.document_loaders import PyMuPDFLoader; print('OK')"
```

**Exit criteria**: All three imports succeed with no errors.

---

### Phase 2 — LLM Layer (OllamaLLM + PromptTemplate)

**Goal**: Replace raw HTTP calls in `llm_service.py` with `OllamaLLM` and `ChatPromptTemplate`.

#### 2.1 New file: `app/llm_chain.py`

This replaces `app/llm_service.py` as the LLM integration point.

```python
# app/llm_chain.py

from langchain_ollama import OllamaLLM
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import PydanticOutputParser, StrOutputParser
from langchain_core.runnables import RunnableWithFallbacks

from app.config import settings
from app.domain.cv_schema import CvExtraction

# ── Prompt ────────────────────────────────────────────────────────────────────

SYSTEM_PROMPT = """\
You are an expert CV parser. Extract structured candidate profile data from the
CV text below. Return ONLY valid JSON that matches the provided schema.
Do not include any markdown fences, prose, or extra keys.

{format_instructions}
"""

HUMAN_PROMPT = """\
CV TEXT:
{cv_text}
"""

_prompt = ChatPromptTemplate.from_messages([
    ("system", SYSTEM_PROMPT),
    ("human", HUMAN_PROMPT),
])

# ── Parser ────────────────────────────────────────────────────────────────────

_pydantic_parser = PydanticOutputParser(pydantic_object=CvExtraction)

# ── LLM ───────────────────────────────────────────────────────────────────────

def _build_llm() -> OllamaLLM:
    return OllamaLLM(
        base_url=settings.ollama_url,
        model=settings.langchain_model,
        temperature=settings.langchain_temperature,
        timeout=settings.llm_timeout,
    )

# ── Chain ─────────────────────────────────────────────────────────────────────

def build_extraction_chain():
    """
    Returns a runnable LCEL chain:
        prompt | llm (with retry) | OutputFixingParser
    Invoke with: chain.invoke({"cv_text": "..."})
    Returns: CvExtraction instance
    """
    from langchain.output_parsers import OutputFixingParser

    llm = _build_llm()

    # base chain: prompt → llm → raw string
    base_chain = _prompt | llm | StrOutputParser()

    # wrap LLM with retry (on exception — network, timeout)
    retrying_chain = base_chain.with_retry(
        retry_if_exception_type=(Exception,),
        stop_after_attempt=settings.llm_max_retries,
        wait_exponential_jitter=False,
    )

    # wrap with self-correction parser
    if settings.output_fixing_enabled:
        fixing_parser = OutputFixingParser.from_llm(
            parser=_pydantic_parser,
            llm=llm,
            max_retries=settings.output_fixing_max_retries,
        )
    else:
        fixing_parser = _pydantic_parser

    return retrying_chain | fixing_parser
```

> **Why `OllamaLLM` vs `ChatOllama`**: `OllamaLLM` is used for the text-completion API (`/api/generate`), which is how the current implementation works. If `llama3` is replaced with a chat-optimised model, switch to `ChatOllama` — only the `build_llm()` call changes.

#### 2.2 Prompt migration from `cv_prompt.txt`

The existing `cv_prompt.txt` contains 15 extraction rules and an output schema. Migrate the rules into the `SYSTEM_PROMPT` in `llm_chain.py`. Add `{format_instructions}` at the end — `PydanticOutputParser` will inject the JSON schema automatically.

The `{{TEXT}}` placeholder is replaced by the `{cv_text}` variable in `HUMAN_PROMPT`.

#### 2.3 Keep `llm_service.py` temporarily

Do not delete `llm_service.py` yet. Keep it as the active path while `llm_chain.py` is being verified. Remove in Phase 8.

**Exit criteria**:
- `build_extraction_chain().invoke({"cv_text": sample_text})` returns a valid `CvExtraction` instance.
- `OllamaLLM` logs appear in output (LangChain verbose mode).

---

### Phase 3 — Output Parser (PydanticOutputParser)

**Goal**: Make `PydanticOutputParser` the single source of truth for JSON parsing and schema validation, replacing the need for `JsonParseGuard` and `SchemaGuard`.

#### 3.1 Understanding `PydanticOutputParser` behaviour

| Scenario | `PydanticOutputParser` response |
|---|---|
| Valid JSON matching `CvExtraction` | Returns `CvExtraction` instance — PASS |
| Valid JSON with extra fields | Pydantic ignores them (or raises if `model_config` forbids) — adjust `CvExtraction` config |
| JSON with wrong types (hard) | Raises `OutputParserException` — `OutputFixingParser` retries |
| Markdown-fenced JSON (` ```json\n...\n``` `) | LangChain strips fences automatically |
| Completely invalid JSON | Raises `OutputParserException` → `OutputFixingParser` retries |
| Self-correction exhausted | Raises — caught in `pipeline.py`, mapped to REJECTED |

#### 3.2 Configure `CvExtraction` for lenient parsing

Add `model_config` to `CvExtraction` in `app/domain/cv_schema.py`:

```python
from pydantic import BaseModel, ConfigDict

class CvExtraction(BaseModel):
    model_config = ConfigDict(extra="ignore")   # allow extra fields from LLM
    ...
```

This preserves the current `SchemaGuard` soft-violation behaviour (extra fields are ignored, not blocked).

#### 3.3 `OutputFixingParser` — self-correction flow

When the LLM returns malformed JSON:

```
LLM output: "Here is the JSON:\n```json\n{bad json}\n```"
    ↓
PydanticOutputParser raises OutputParserException("Could not parse...")
    ↓
OutputFixingParser sends correction prompt back to LLM:
    "The previous output had the following error: ...
     Try to fix it. Output ONLY the corrected JSON."
    ↓
LLM returns corrected JSON
    ↓
PydanticOutputParser succeeds → CvExtraction instance
```

#### 3.4 Map `OutputParserException` to guardrail reports

In `pipeline.py`, catch `OutputParserException` after the chain invocation and convert it to a `BLOCK` report, preserving the audit trail:

```python
from langchain_core.exceptions import OutputParserException

try:
    cv_data: CvExtraction = extraction_chain.invoke({"cv_text": prompt_text})
    ctx.cv_data = cv_data
    ctx.raw_dict = cv_data.model_dump()
except OutputParserException as exc:
    ctx.reports.append(GuardrailReport(
        guard="extraction_chain",
        status=GuardrailStatus.BLOCK,
        reason=f"LLM output could not be parsed after self-correction attempts: {exc}",
    ))
```

**Exit criteria**:
- Sample good CV text → `CvExtraction` returned correctly.
- Sample malformed JSON from LLM stub → `OutputFixingParser` retries → succeeds on second attempt.
- Self-correction exhausted → `OutputParserException` raised → mapped to BLOCK report.

---

### Phase 4 — Extraction Chain (LCEL)

**Goal**: Compose the full extraction chain with retry and fallback using LCEL operators.

#### 4.1 Chain composition in `llm_chain.py`

```python
# Full chain with retry + self-correction:
# (prompt → llm → str).with_retry(n=3) → OutputFixingParser[CvExtraction]

extraction_chain = build_extraction_chain()
```

#### 4.2 Circuit breaker integration

LangChain does not have a built-in circuit breaker. Two options:

**Option A — Keep custom `_CircuitBreaker`** (Recommended for Phase 4):

Wrap the chain invocation in a circuit-breaker guard in `pipeline.py`:

```python
# In Pipeline.run()
if not circuit_breaker.allow_request():
    raise CircuitOpenError("Circuit breaker is OPEN — Ollama unreachable")

try:
    result = extraction_chain.invoke({"cv_text": prompt_text})
    circuit_breaker.record_success()
except Exception as exc:
    circuit_breaker.record_failure()
    raise
```

Move `_CircuitBreaker` from `llm_service.py` to `app/circuit_breaker.py` as a standalone module.

**Option B — Use `.with_fallbacks()`** (Future enhancement):

```python
# Dead-chain that immediately raises CircuitOpenError
from langchain_core.runnables import RunnableLambda

dead_chain = RunnableLambda(lambda _: (_ for _ in ()).throw(CircuitOpenError()))

# Main chain falls back to dead_chain after N retries
fault_tolerant_chain = extraction_chain.with_fallbacks([dead_chain])
```

This does not replicate the FSM (CLOSED/OPEN/HALF-OPEN) behaviour, so Option A is preferred.

#### 4.3 `.with_retry()` configuration

```python
retrying_llm = (prompt | llm).with_retry(
    retry_if_exception_type=(
        httpx.ConnectError,
        httpx.TimeoutException,
        httpx.RemoteProtocolError,
    ),
    stop_after_attempt=settings.llm_max_retries,
    wait_exponential_multiplier=settings.llm_retry_delay,
)
```

This replaces the hand-rolled loop in `llm_service.py`. Retry is applied **before** the parser — a network error retries the LLM call, while a parse error triggers `OutputFixingParser`.

**Exit criteria**:
- Simulating an Ollama timeout (kill Ollama mid-run) → `.with_retry()` retries 3 times → circuit breaker records failures → OPEN after 5.
- Circuit OPEN → `CircuitOpenError` → pipeline maps to ERROR → dead-letter.

---

### Phase 5 — Document Loaders

**Goal**: Replace custom `parsers.py` with LangChain community document loaders.

#### 5.1 New file: `app/document_loader.py`

```python
# app/document_loader.py

from pathlib import Path
from langchain_community.document_loaders import (
    PyMuPDFLoader,
    Docx2txtLoader,
    UnstructuredImageLoader,
)
from langchain_text_splitters import RecursiveCharacterTextSplitter
from app.config import settings
import logging

logger = logging.getLogger(__name__)

_SUPPORTED_EXTENSIONS = {
    ".pdf": PyMuPDFLoader,
    ".docx": Docx2txtLoader,
    ".doc": Docx2txtLoader,
    ".jpg": UnstructuredImageLoader,
    ".jpeg": UnstructuredImageLoader,
    ".png": UnstructuredImageLoader,
    ".tiff": UnstructuredImageLoader,
    ".bmp": UnstructuredImageLoader,
}

def load_document_text(file_path: str) -> str:
    """
    Load text from a document file using the appropriate LangChain loader.
    Returns concatenated page content as a single string.
    Raises ValueError for unsupported file types.
    """
    path = Path(file_path)
    ext = path.suffix.lower()
    loader_cls = _SUPPORTED_EXTENSIONS.get(ext)

    if loader_cls is None:
        raise ValueError(f"Unsupported file extension: {ext}")

    loader = loader_cls(str(file_path))
    documents = loader.load()
    raw_text = "\n\n".join(doc.page_content for doc in documents)

    logger.info("document_loader: extracted %d chars from %s", len(raw_text), path.name)
    return raw_text
```

#### 5.2 Long document handling with `RecursiveCharacterTextSplitter`

For CVs that exceed `max_text_chars` (currently 40 000), the `TextLengthGuard` truncates raw text. With LangChain we have a better option: split into chunks, extract each chunk, then merge results.

This is an **optional enhancement** (Phase 5b, after core chain is stable):

```python
def load_and_split(file_path: str, chunk_size: int = 40_000) -> list[str]:
    """Split a long document into overlapping chunks for batch extraction."""
    text = load_document_text(file_path)
    if len(text) <= chunk_size:
        return [text]

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=chunk_size,
        chunk_overlap=500,
        separators=["\n\n", "\n", " ", ""],
    )
    return splitter.split_text(text)
```

If multiple chunks are returned, the pipeline runs extraction on each chunk and merges `workExperiences`, `educations`, etc. arrays. The merge logic lives in a new `app/merge.py` module. Mark this as **future work** in the plan — implement Phase 5b only if chunk support is required.

#### 5.3 Update `TextExtractor` and `LiteParseTextExtractor` guards

Replace the calls to `parsers.extract_text()` and `liteparse_service.extract_text_liteparse()` inside the guard `run()` methods with calls to `document_loader.load_document_text()`.

```python
# app/guardrails/input/text_extractor.py  (after)

from app.document_loader import load_document_text

class TextExtractor:
    def run(self, ctx: PipelineContext) -> GuardrailReport:
        try:
            ctx.raw_text = load_document_text(ctx.file_path)
            return GuardrailReport(guard="text_extractor", status=GuardrailStatus.PASS, reason="ok")
        except Exception as exc:
            return GuardrailReport(guard="text_extractor", status=GuardrailStatus.BLOCK,
                                   reason=f"text_extractor: failed to load document — {exc}")
```

> **LiteParse mode**: `LiteParseTextExtractor` guard can remain as-is (calling the `lit` CLI), since LangChain does not have a LiteParse loader. The fallback in `liteparse_service.py` should be updated to use `load_document_text()` instead of the old `parsers.extract_text()`.

#### 5.4 Delete `parsers.py` (after verification)

`parsers.py` becomes dead code once all loaders go through `document_loader.py`. Remove in Phase 10.

**Exit criteria**:
- PDF, DOCX, PNG test files all load correctly via `document_loader.load_document_text()`.
- Same character counts (± 5%) as old `parsers.py` output.
- `parsers.py` is no longer imported anywhere.

---

### Phase 6 — Guard Simplification

**Goal**: Remove `JsonParseGuard` and `SchemaGuard` from the output pipeline since `PydanticOutputParser` covers their responsibilities.

#### 6.1 What each guard did and what covers it now

| Guard | Responsibility | New Handler |
|---|---|---|
| `JsonParseGuard` | Strip markdown fences, `json.loads()`, extract `{...}` block | `PydanticOutputParser` (LangChain strips fences internally) |
| `SchemaGuard` | Validate against `CvExtraction`, classify hard vs soft violations | `PydanticOutputParser` raises `OutputParserException` on hard violations; `model_config(extra="ignore")` handles soft violations (extra fields) |

> **Important**: The `SchemaGuard` currently distinguishes hard violations (BLOCK) from soft violations (WARN → DEGRADED). `PydanticOutputParser` by default raises on any validation error. To preserve the DEGRADED path for soft violations, update `CvExtraction` to use `model_config(extra="ignore")` and set optional fields (missing lists) to defaults — this was already the intent but is now enforced at model level.

#### 6.2 Remove guards from pipeline

In `app/pipeline.py`, update `_OUTPUT_PIPELINE`:

```python
# Before
_OUTPUT_PIPELINE = GuardrailPipeline([
    JsonParseGuard(),
    SchemaGuard(),
    SemanticGuard(),
    ConfidenceGuard(),
    SanitizeGuard(),
])

# After
_OUTPUT_PIPELINE = GuardrailPipeline([
    # JsonParseGuard — removed: handled by PydanticOutputParser
    # SchemaGuard    — removed: handled by PydanticOutputParser
    SemanticGuard(),
    ConfidenceGuard(),
    SanitizeGuard(),
])
```

#### 6.3 Update `PipelineContext`

The `ctx.raw_dict` field was populated by `JsonParseGuard`. It is now populated directly from `cv_data.model_dump()` after the chain invocation. Update `PipelineContext` accordingly:

```python
# In Pipeline.run(), after successful chain invocation:
ctx.cv_data = cv_extraction_result       # CvExtraction instance
ctx.raw_dict = cv_extraction_result.model_dump()  # for remaining guards

# Then run the simplified output pipeline
output_reports = _OUTPUT_PIPELINE.run(ctx)
```

The remaining output guards (`SemanticGuard`, `ConfidenceGuard`, `SanitizeGuard`) read from `ctx.cv_data` directly — no change needed to their internals.

#### 6.4 Guard files to delete

- `app/guardrails/output/json_parse.py` — removed
- `app/guardrails/output/schema.py` — removed

Delete in Phase 10 after full verification.

**Exit criteria**:
- Output pipeline runs with 3 guards (semantic, confidence, sanitize) instead of 5.
- Good CV → PASS with correct `CvExtraction`.
- Bad JSON (simulated) → `OutputParserException` → BLOCK report in `ctx.reports` → REJECTED status.
- Soft violation (extra field from LLM) → ignored silently, no WARN → PASS or DEGRADED from other guards only.

---

### Phase 7 — Circuit Breaker Migration

**Goal**: Extract `_CircuitBreaker` from `llm_service.py` into a standalone `app/circuit_breaker.py` module, decouple it from the now-deleted HTTP logic.

#### 7.1 New file: `app/circuit_breaker.py`

Move the `_CircuitBreaker` class and `CircuitOpenError` exception from `llm_service.py` into this new file. No logic changes.

```python
# app/circuit_breaker.py
import threading, time, logging
from enum import Enum

class CircuitState(Enum):
    CLOSED = "CLOSED"
    OPEN = "OPEN"
    HALF_OPEN = "HALF_OPEN"

class CircuitOpenError(RuntimeError):
    pass

class CircuitBreaker:
    """Thread-safe circuit breaker (CLOSED → OPEN → HALF_OPEN)."""
    # ... (identical to current _CircuitBreaker implementation)
```

#### 7.2 Instantiate in `llm_chain.py`

```python
# app/llm_chain.py
from app.circuit_breaker import CircuitBreaker, CircuitOpenError

_circuit_breaker = CircuitBreaker(
    failure_threshold=settings.cb_failure_threshold,
    window_seconds=settings.cb_window_seconds,
    cooldown_seconds=settings.cb_cooldown_seconds,
)

def invoke_extraction(cv_text: str) -> CvExtraction:
    """Public API: run extraction chain with circuit breaker protection."""
    if not _circuit_breaker.allow_request():
        raise CircuitOpenError("Circuit breaker is OPEN")
    try:
        result = _extraction_chain.invoke({"cv_text": cv_text})
        _circuit_breaker.record_success()
        return result
    except Exception:
        _circuit_breaker.record_failure()
        raise
```

#### 7.3 Update `pipeline.py`

Replace the `ask_llm()` call with `invoke_extraction()` from `llm_chain.py`.

```python
from app.llm_chain import invoke_extraction
from app.circuit_breaker import CircuitOpenError

# In Pipeline.run():
try:
    ctx.cv_data = invoke_extraction(prompt_text)
    ctx.raw_dict = ctx.cv_data.model_dump()
except CircuitOpenError as exc:
    ctx.reports.append(GuardrailReport(
        guard="circuit_breaker",
        status=GuardrailStatus.BLOCK,
        reason=str(exc),
    ))
except OutputParserException as exc:
    ctx.reports.append(GuardrailReport(
        guard="extraction_chain",
        status=GuardrailStatus.BLOCK,
        reason=f"output could not be parsed: {exc}",
    ))
```

**Exit criteria**:
- Simulate Ollama down → circuit opens after 5 failures → subsequent calls fast-fail.
- Ollama back up after cooldown → circuit probes and closes.

---

### Phase 8 — Pipeline Rewire

**Goal**: Update `pipeline.py` to remove all references to old modules and finalize the new flow.

#### 8.1 Updated `pipeline.py` structure

```python
# app/pipeline.py

from app.guardrails.base import GuardrailPipeline, PipelineContext, GuardrailStatus, GuardrailReport
from app.guardrails.input.file_size import FileSizeGuard
from app.guardrails.input.mime_type import MimeTypeGuard
from app.guardrails.input.text_extractor import TextExtractor
from app.guardrails.input.liteparse_extractor import LiteParseTextExtractor
from app.guardrails.input.text_length import TextLengthGuard
from app.guardrails.input.text_quality import TextQualityGuard
from app.guardrails.input.injection import InjectionGuard
from app.guardrails.output.semantic import SemanticGuard
from app.guardrails.output.confidence import ConfidenceGuard
from app.guardrails.output.sanitize import SanitizeGuard
from app.llm_chain import invoke_extraction
from app.circuit_breaker import CircuitOpenError
from app.backend_client import get_extraction_mode
from langchain_core.exceptions import OutputParserException

_INPUT_PIPELINE = GuardrailPipeline([
    FileSizeGuard(), MimeTypeGuard(), TextExtractor(),
    TextLengthGuard(), TextQualityGuard(), InjectionGuard(),
])

_INPUT_PIPELINE_LITE = GuardrailPipeline([
    FileSizeGuard(), MimeTypeGuard(), LiteParseTextExtractor(),
    TextLengthGuard(), TextQualityGuard(), InjectionGuard(),
])

_OUTPUT_PIPELINE = GuardrailPipeline([
    SemanticGuard(), ConfidenceGuard(), SanitizeGuard(),
])
```

#### 8.2 LLM invocation block

```python
# In Pipeline.run() — replaces ask_llm() call
use_llm = get_extraction_mode(category_id)
input_pipeline = _INPUT_PIPELINE_LITE if not use_llm else _INPUT_PIPELINE
input_reports = input_pipeline.run(ctx)

if not any(r.status == GuardrailStatus.BLOCK for r in input_reports):
    prompt_text = ctx.prompt_text or ctx.raw_text
    try:
        ctx.cv_data = invoke_extraction(prompt_text)
        ctx.raw_dict = ctx.cv_data.model_dump()
    except (CircuitOpenError, OutputParserException, Exception) as exc:
        ctx.reports.append(...)  # see Phase 7.3

output_reports = _OUTPUT_PIPELINE.run(ctx)
```

#### 8.3 Files to delete after Phase 8

- `app/llm_service.py` — replaced by `app/llm_chain.py` + `app/circuit_breaker.py`
- `app/parsers.py` — replaced by `app/document_loader.py`
- `app/guardrails/output/json_parse.py` — replaced by `PydanticOutputParser`
- `app/guardrails/output/schema.py` — replaced by `PydanticOutputParser`
- `cv_prompt.txt` — content migrated to `ChatPromptTemplate` in `llm_chain.py`

**Exit criteria**:
- Full end-to-end test: upload a real PDF CV → correct `CvExtraction` JSON written to `/app/output/`.
- No references to deleted modules anywhere in the codebase.
- All existing tests pass.

---

### Phase 9 — Observability (LangSmith)

**Goal**: Add optional LangSmith tracing for prompt → LLM → parsed output visibility.

> This phase is **optional** and has no impact on correctness. Enable only when `LANGSMITH_ENABLED=true` and `LANGCHAIN_API_KEY` are set.

#### 9.1 Environment variables

LangChain reads these automatically:

```env
LANGCHAIN_TRACING_V2=true
LANGCHAIN_API_KEY=<your_langsmith_key>
LANGCHAIN_PROJECT=cv-batch-extractor
```

No code changes needed — LangSmith tracing activates at the SDK level when these env vars are present.

#### 9.2 Add config guard in `main.py`

```python
import os
if settings.langsmith_enabled:
    os.environ.setdefault("LANGCHAIN_TRACING_V2", "true")
    os.environ.setdefault("LANGCHAIN_PROJECT", settings.langsmith_project)
    logger.info("LangSmith tracing enabled — project: %s", settings.langsmith_project)
```

#### 9.3 What LangSmith will show

For every CV processed:
- Full system + human prompt (with CV text)
- Raw LLM response string
- `OutputFixingParser` correction prompt (if triggered)
- Final parsed `CvExtraction` JSON
- Latency per step, token counts

**Exit criteria** (if LangSmith key available):
- Process one CV → visible trace in LangSmith UI with all steps.

---

### Phase 10 — Cleanup & Verification

**Goal**: Remove all old code, update documentation, run final integration test.

#### 10.1 Files to delete

```
app/llm_service.py
app/parsers.py
app/guardrails/output/json_parse.py
app/guardrails/output/schema.py
cv_prompt.txt
```

#### 10.2 Documentation updates

| Doc File | Change |
|---|---|
| `docs/ARCHITECTURE.md` | Update pipeline diagram, remove `JsonParseGuard`/`SchemaGuard`, add LangChain chain section |
| `docs/GUARDRAILS_SPEC.md` | Remove specifications for `JsonParseGuard` and `SchemaGuard`; add `PydanticOutputParser` behaviour note |
| `docs/DOMAIN_MODEL.md` | Add `model_config = ConfigDict(extra="ignore")` to `CvExtraction` spec |
| `README.md` (root) | Update guard count (11 → 9), add LangChain to tech stack table |

#### 10.3 Final integration test checklist

- [ ] Upload valid PDF CV → status PASS → JSON written → backend notified
- [ ] Upload valid DOCX CV → status PASS
- [ ] Upload image (PNG) CV → status PASS or DEGRADED (OCR quality dependent)
- [ ] Upload oversized file (> 20 MB) → REJECTED → dead-letter entry
- [ ] Upload non-CV PDF → low-confidence DEGRADED
- [ ] Simulate Ollama down → circuit breaker opens → ERROR → dead-letter
- [ ] Simulate malformed LLM JSON → `OutputFixingParser` retries → PASS
- [ ] Simulate self-correction exhausted → REJECTED → dead-letter
- [ ] Category with LiteParse mode → `LiteParseTextExtractor` used → same final result
- [ ] LangSmith trace visible for each run (if enabled)

---

## 6. File-by-File Change Matrix

| File | Action | Reason |
|---|---|---|
| `requirements.txt` | **MODIFY** — add LangChain packages | New dependencies |
| `app/config.py` | **MODIFY** — add LangChain settings | Provider config, temperatures, LangSmith |
| `app/domain/cv_schema.py` | **MODIFY** — add `model_config = ConfigDict(extra="ignore")` | Lenient parsing for `PydanticOutputParser` |
| `app/llm_chain.py` | **CREATE** | LangChain extraction chain (replaces `llm_service.py`) |
| `app/circuit_breaker.py` | **CREATE** | Extracted from `llm_service.py` |
| `app/document_loader.py` | **CREATE** | LangChain document loaders (replaces `parsers.py`) |
| `app/pipeline.py` | **MODIFY** | Remove old imports, add `invoke_extraction`, simplify output pipeline |
| `app/guardrails/input/text_extractor.py` | **MODIFY** | Use `document_loader.load_document_text()` instead of `parsers.extract_text()` |
| `app/liteparse_service.py` | **MODIFY** | Update fallback to use `document_loader.load_document_text()` |
| `main.py` | **MODIFY** | Add optional LangSmith env var setup |
| `app/llm_service.py` | **DELETE** | Replaced by `llm_chain.py` + `circuit_breaker.py` |
| `app/parsers.py` | **DELETE** | Replaced by `document_loader.py` |
| `app/guardrails/output/json_parse.py` | **DELETE** | Replaced by `PydanticOutputParser` |
| `app/guardrails/output/schema.py` | **DELETE** | Replaced by `PydanticOutputParser` |
| `cv_prompt.txt` | **DELETE** | Migrated to `ChatPromptTemplate` in `llm_chain.py` |
| `docs/ARCHITECTURE.md` | **MODIFY** | Reflect new pipeline diagram |
| `docs/GUARDRAILS_SPEC.md` | **MODIFY** | Remove two guard specs |
| `docs/DOMAIN_MODEL.md` | **MODIFY** | Add `model_config` note |
| `README.md` (root) | **MODIFY** | Update tech stack and guard count |

**Unchanged files** (no modifications needed):

| File | Reason unchanged |
|---|---|
| `app/guardrails/base.py` | Foundation types — fully compatible |
| `app/guardrails/input/file_size.py` | No dependency on LLM or parsers |
| `app/guardrails/input/mime_type.py` | No dependency on LLM or parsers |
| `app/guardrails/input/text_length.py` | Reads `ctx.raw_text` — unchanged |
| `app/guardrails/input/text_quality.py` | Reads `ctx.raw_text` — unchanged |
| `app/guardrails/input/injection.py` | Reads `ctx.raw_text` — unchanged |
| `app/guardrails/input/liteparse_extractor.py` | Minor: update fallback import |
| `app/guardrails/output/semantic.py` | Reads `ctx.cv_data` — unchanged |
| `app/guardrails/output/confidence.py` | Reads `ctx.cv_data` — unchanged |
| `app/guardrails/output/sanitize.py` | Mutates `ctx.cv_data` — unchanged |
| `app/worker.py` | Calls `Pipeline.run()` — unchanged |
| `app/watcher.py` | Calls `worker.submit()` — unchanged |
| `app/dead_letter.py` | Unchanged |
| `app/backend_client.py` | Unchanged |

---

## 7. Guard Responsibility After Refactor

### Input Pipeline (6 guards — unchanged)

| # | Guard | Responsibility | Status Options |
|---|---|---|---|
| 1 | `FileSizeGuard` | Block files > 20 MB | PASS / BLOCK |
| 2 | `MimeTypeGuard` | Block non-document MIME types | PASS / BLOCK |
| 3 | `TextExtractor` / `LiteParseTextExtractor` | Load document text via LangChain loaders | PASS / BLOCK |
| 4 | `TextLengthGuard` | Block empty; warn + truncate if oversized | PASS / WARN / BLOCK |
| 5 | `TextQualityGuard` | Warn on low word count / garbled OCR | PASS / WARN |
| 6 | `InjectionGuard` | Sanitize prompt-override patterns | PASS / WARN |

### LangChain Extraction Chain (replaces LLM call + 2 output guards)

| Component | Responsibility |
|---|---|
| `ChatPromptTemplate` | Format system + human messages with CV text and format instructions |
| `OllamaLLM` with `.with_retry(n=3)` | Call Ollama, retry on network/timeout errors |
| `PydanticOutputParser[CvExtraction]` | Deserialize JSON → `CvExtraction`, raise on hard violations |
| `OutputFixingParser` | Self-correct malformed JSON via second LLM call |
| `CircuitBreaker` | Fast-fail when Ollama is repeatedly down |

### Output Pipeline (3 guards — reduced from 5)

| # | Guard | Responsibility | Status Options |
|---|---|---|---|
| ~~1~~ | ~~`JsonParseGuard`~~ | ~~JSON extraction + fence stripping~~ | ~~Removed~~ |
| ~~2~~ | ~~`SchemaGuard`~~ | ~~Pydantic schema validation~~ | ~~Removed~~ |
| 3 | `SemanticGuard` | Date ordering, ISO codes, email/phone | PASS / WARN |
| 4 | `ConfidenceGuard` | Annotate with LLM self-reported confidence | PASS / WARN |
| 5 | `SanitizeGuard` | Strip control chars, cap field lengths | PASS (always) |

---

## 8. Testing Strategy

### Unit Tests

| Test Target | What to Test |
|---|---|
| `llm_chain.build_extraction_chain()` | Chain builds without errors |
| `invoke_extraction(good_text)` | Returns valid `CvExtraction` (use Ollama mock/stub) |
| `invoke_extraction(bad_text)` | `OutputFixingParser` retries; on exhaustion raises `OutputParserException` |
| `CircuitBreaker` | State transitions: CLOSED → OPEN → HALF_OPEN |
| `document_loader.load_document_text(pdf)` | Correct text extracted from test PDF |
| `document_loader.load_document_text(docx)` | Correct text extracted from test DOCX |
| `document_loader.load_document_text(png)` | Correct text extracted from test image |
| `SemanticGuard` | Unchanged from current tests |
| `ConfidenceGuard` | Unchanged from current tests |
| `SanitizeGuard` | Unchanged from current tests |

### Integration Tests

| Test | Expected Outcome |
|---|---|
| Real PDF CV → `invoke_extraction()` | `CvExtraction` with fullName populated |
| Ollama returns markdown-fenced JSON | `OutputFixingParser` strips fences, returns `CvExtraction` |
| Ollama returns invalid JSON (mocked) | After `output_fixing_max_retries`, `OutputParserException` raised |
| 5 Ollama timeouts (mocked) | `CircuitBreaker` opens, next call fast-fails |
| After cooldown (mocked) | `CircuitBreaker` enters HALF_OPEN, probe succeeds, closes |
| Full pipeline run with real CV | `ProcessingResult.status == "PASS"`, JSON file written |

### Regression Tests (Preserve Existing Behaviour)

| Behaviour | Test |
|---|---|
| REJECTED documents go to dead-letter | Same as before |
| Backend notification on PASS/DEGRADED | Same as before |
| LiteParse mode selected per category | `get_extraction_mode()` returns false → `_INPUT_PIPELINE_LITE` used |
| Injection guard sanitizes prompt text | `ctx.prompt_text` populated when injection detected |
| Oversized text truncated before LLM call | `TextLengthGuard` still truncates at 40 000 chars |

---

## 9. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `OllamaLLM` produces different token format from raw API | Medium | High | Test output parity before removing `llm_service.py` |
| `PydanticOutputParser` stricter than `SchemaGuard` (rejects previously-DEGRADED CVs) | Medium | Medium | Add `model_config(extra="ignore")` to `CvExtraction`; test with the same CV corpus |
| `OutputFixingParser` doubles LLM call cost on bad output | Low | Low | Already paying retries today; `OutputFixingParser` is smarter (sends error context) |
| `PyMuPDFLoader` text differs from `PyMuPDF` direct usage | Low | Medium | Compare char counts and content on 10 sample CVs before removing `parsers.py` |
| LangChain minor version breaks API | Low | Medium | Pin LangChain to minor version (`langchain>=0.3.0,<0.4.0`) |
| LangSmith sends CV text to cloud | Low | High | LangSmith is opt-in; default `langsmith_enabled=false`; document in README |
| Circuit breaker no longer collocated with LLM logic | Low | Low | Move to `circuit_breaker.py` standalone module — easy to find |
| `liteparse_service.py` fallback breaks after `parsers.py` removal | Medium | Low | Update fallback import to `document_loader.load_document_text()` in Phase 5 |

---

## 10. Migration Checklist

### Phase 1 — Dependencies & Config
- [ ] Add LangChain packages to `requirements.txt`
- [ ] Extend `app/config.py` with LangChain settings
- [ ] Verify all LangChain imports succeed in Docker container

### Phase 2 — LLM Layer
- [ ] Create `app/llm_chain.py` with `OllamaLLM` and `ChatPromptTemplate`
- [ ] Migrate extraction rules from `cv_prompt.txt` to `SYSTEM_PROMPT` in `llm_chain.py`
- [ ] Test `build_extraction_chain().invoke()` with sample text

### Phase 3 — Output Parser
- [ ] Add `model_config = ConfigDict(extra="ignore")` to `CvExtraction`
- [ ] Add `PydanticOutputParser` and `OutputFixingParser` to chain
- [ ] Test self-correction with mocked malformed LLM output

### Phase 4 — Extraction Chain
- [ ] Add `.with_retry()` to chain
- [ ] Verify retry fires on network errors (not on parse errors)
- [ ] Verify `OutputFixingParser` fires on parse errors (not on network errors)

### Phase 5 — Document Loaders
- [ ] Create `app/document_loader.py` with `PyMuPDFLoader`, `Docx2txtLoader`, `UnstructuredImageLoader`
- [ ] Update `TextExtractor` guard to use `document_loader.load_document_text()`
- [ ] Update `liteparse_service.py` fallback to use `document_loader.load_document_text()`
- [ ] Test PDF, DOCX, PNG text extraction parity with old `parsers.py`

### Phase 6 — Guard Simplification
- [ ] Remove `JsonParseGuard` and `SchemaGuard` from `_OUTPUT_PIPELINE` in `pipeline.py`
- [ ] Verify `ctx.raw_dict` is populated from `ctx.cv_data.model_dump()`
- [ ] Test that extra LLM fields are silently ignored

### Phase 7 — Circuit Breaker Migration
- [ ] Create `app/circuit_breaker.py` with extracted `CircuitBreaker` class
- [ ] Add `invoke_extraction()` function to `llm_chain.py`
- [ ] Test circuit breaker state transitions

### Phase 8 — Pipeline Rewire
- [ ] Update `pipeline.py` to use `invoke_extraction()` and simplified output pipeline
- [ ] Run full end-to-end test with real CV file
- [ ] Delete `app/llm_service.py`, `app/parsers.py`, `app/guardrails/output/json_parse.py`, `app/guardrails/output/schema.py`, `cv_prompt.txt`

### Phase 9 — Observability (Optional)
- [ ] Add LangSmith env var setup in `main.py` (gated by `langsmith_enabled`)
- [ ] Verify trace appears in LangSmith UI (if API key available)

### Phase 10 — Cleanup & Verification
- [ ] Update `docs/ARCHITECTURE.md`
- [ ] Update `docs/GUARDRAILS_SPEC.md`
- [ ] Update `docs/DOMAIN_MODEL.md`
- [ ] Update `README.md` (root) — tech stack table and guard count
- [ ] Run full integration test checklist from Phase 10.3
- [ ] Confirm no references to deleted modules in codebase (`grep -r "llm_service\|from app.parsers\|JsonParseGuard\|SchemaGuard"`)

---

*Document version: 1.0 — May 2026*
*Branch: langchain*
*Author: Engineering Team*
