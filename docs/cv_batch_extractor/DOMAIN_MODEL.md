# Document Batch Extractor — Domain Model

---

## 1. ValidationReport

The atomic unit of pipeline output. Every stage appends one (or more) `ValidationReport` instances to `PipelineContext.reports`.

```python
# domain/models.py

@dataclass
class ValidationReport:
    validator: str                               # e.g. "file_size", "text_quality", "schema"
    status: Literal["PASS", "WARN", "BLOCK"]
    reason: str | None = None
    metadata: dict = field(default_factory=dict)
```

- `BLOCK` — stage failed irrecoverably; pipeline short-circuits after this report.
- `WARN` — anomaly detected; pipeline continues; result is `DEGRADED`.
- `PASS` — stage completed cleanly.

---

## 2. PipelineContext

Single mutable object that flows through all 10 pipeline stages. Each stage reads from it and writes its outputs into it.

```python
# domain/models.py

@dataclass
class PipelineContext:
    # ── identity ──────────────────────────────────────────────────────
    document_id: str
    category_id: str
    file_path: str

    # ── stage 1: classification ───────────────────────────────────────
    document_type: str | None = None          # "pdf" | "docx" | "doc" | "image" | "unknown"

    # ── stage 3: preprocessing ────────────────────────────────────────
    preprocessed_path: str | None = None      # path after format conversion / image enhancement

    # ── stage 4: OCR ─────────────────────────────────────────────────
    raw_text: str | None = None               # extracted text (may be truncated by stage 5)
    ocr_confidence: float | None = None       # engine-reported confidence [0.0–1.0]

    # ── stage 6: layout, entity, metadata extraction ──────────────────
    layout: dict | None = None                # section headings, paragraphs
    tables: list[dict] = field(default_factory=list)
    key_values: dict = field(default_factory=dict)
    entities: dict = field(default_factory=dict)   # {"emails": [...], "phones": [...]}
    doc_metadata: dict = field(default_factory=dict)

    # ── stage 5 (injection): LLM-safe text ───────────────────────────
    prompt_text: str | None = None            # raw_text with [REDACTED] substitutions applied

    # ── stage 7: chunking ─────────────────────────────────────────────
    chunks: list[str] = field(default_factory=list)

    # ── stage 8: LLM ─────────────────────────────────────────────────
    llm_raw: str | None = None                # raw string from LLM

    # ── stage 9: validation ───────────────────────────────────────────
    raw_dict: dict | None = None              # parsed JSON from llm_raw (CV path)
    cv_data: CvExtraction | None = None       # Pydantic-validated model (CV path)
    knowledge_data: KnowledgeExtraction | None = None  # Pydantic-validated model (TECHNICAL path)

    # ── full audit trail ──────────────────────────────────────────────
    reports: list[ValidationReport] = field(default_factory=list)
```

---

## 3. ProcessingResult

Returned by `ExtractionPipeline.run()` and consumed by `workflow/orchestration.py` to notify the backend and write to the dead-letter log.

```python
# domain/models.py

@dataclass
class ProcessingResult:
    document_id: str
    category_id: str
    status: Literal["PASS", "DEGRADED", "REJECTED", "ERROR"]
    cv_data: CvExtraction | None          # populated on CV path
    output_file: str | None               # filename in output_dir; None on REJECTED / ERROR
    knowledge_data: KnowledgeExtraction | None = None  # populated on TECHNICAL path

    reports: list[ValidationReport] = field(default_factory=list)
    error: str | None = None              # populated on ERROR only

    @property
    def warnings(self) -> list[str]:
        return [r.reason for r in self.reports if r.status == "WARN" and r.reason]
```

---

## 4. KnowledgeExtraction (Pydantic Schema)

Single source of truth for extracted technical-document knowledge. Stored as JSON in `output_dir` and sent to the backend via `POST /api/v1/knowledge/ingest`.

```python
# domain/technical_schema.py

class TechEntity(BaseModel):
    name: str
    version: str | None = None
    category: str | None = None          # language|framework|library|database|tool|protocol|cloud_service|platform|other
    aliases: list[str] = Field(default_factory=list)


class ConceptEntity(BaseModel):
    name: str
    definition: str | None = None
    relatedConcepts: list[str] = Field(default_factory=list)


class Relationship(BaseModel):
    source: str                          # must match a TechEntity or ConceptEntity name
    target: str
    relationType: str                    # depends_on|uses|implements|extends|replaces|part_of|…
    weight: float | None = None          # 0.0–1.0 confidence; default 1.0


class KnowledgeExtraction(BaseModel):
    title: str | None = None
    summary: str | None = None
    technologies: list[TechEntity] = Field(default_factory=list)
    concepts: list[ConceptEntity] = Field(default_factory=list)
    relationships: list[Relationship] = Field(default_factory=list)
```

---

## 5. CvExtraction (Pydantic Schema)

Single source of truth for extracted CV structure. Stored as JSON in `output_dir` and sent to the backend via `/api/v1/cv-candidates`.

```python
# domain/cv_schema.py

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

## 6. Settings

All configuration via environment variables. Loaded once at startup by `config/settings.py`.

```python
# config/settings.py  (Pydantic BaseSettings)

class Settings(BaseSettings):
    # paths
    upload_dir: str = "/app/uploads"
    output_dir: str = "/app/output"

    # backend
    backend_url: str = "http://backend:8080"
    backend_timeout: int = 30
    internal_api_key: str = ""

    # OCR engine: "liteparser" | "paddleocr" | "tesseract"
    ocr_engine: str = "liteparser"
    liteparse_timeout: int = 60

    # LLM provider: "ollama" | "openai" | "azure_openai" | "anthropic"
    llm_provider: str = "ollama"
    llm_timeout: int = 300
    llm_max_retries: int = 3
    llm_retry_delay: float = 2.0

    ollama_url: str = "http://host.docker.internal:11434"
    ollama_model: str = "llama3"

    openai_api_key: str = ""
    openai_model: str = "gpt-4o"

    azure_openai_endpoint: str = ""
    azure_openai_api_key: str = ""
    azure_openai_deployment: str = ""
    azure_openai_api_version: str = "2024-02-01"

    anthropic_api_key: str = ""
    anthropic_model: str = "claude-sonnet-4-6"

    # input validation thresholds
    max_file_size_mb: float = 20.0
    max_text_chars: int = 40_000
    min_text_chars: int = 50
    min_word_count: int = 30
    min_printable_ratio: float = 0.85
    allowed_mime_types: list[str] = [...]   # PDF, DOCX, DOC, JPEG, PNG, TIFF, BMP
    injection_patterns: list[str] = [...]   # 7 regex patterns (see GUARDRAILS_SPEC.md)

    # worker pool
    worker_max_workers: int = 4
    worker_queue_size: int = 100

    # circuit breaker (Ollama only)
    cb_failure_threshold: int = 5
    cb_window_seconds: int = 60
    cb_cooldown_seconds: int = 30

    # chunking
    chunk_size: int = 4_000
    chunk_overlap: int = 200

    # output field length caps (chars)
    sanitize_max_full_name: int = 200
    sanitize_max_email: int = 320
    sanitize_max_summary: int = 5_000
    sanitize_max_list_item: int = 1_000
    sanitize_max_default: int = 500

    model_config = {"env_file": ".env"}
```

---

## 7. OCR Adapter Contract

```python
# ocr/base.py

@dataclass
class OCRResult:
    text: str
    pages: int = 1
    confidence: float | None = None
    engine: str = "unknown"

@runtime_checkable
class OCRAdapter(Protocol):
    def extract(self, file_path: str) -> OCRResult: ...
    def is_available(self) -> bool: ...
```

| Adapter | Module | Notes |
|---|---|---|
| `LiteParserAdapter` | `ocr/liteparser_adapter/adapter.py` | CLI call; builtin fallback (PyMuPDF/python-docx/pytesseract) |
| `PaddleOCRAdapter` | `ocr/paddleocr_adapter/adapter.py` | Stub — set `OCR_ENGINE=paddleocr` + install paddlepaddle |
| `TesseractAdapter` | `ocr/tesseract_adapter/adapter.py` | pytesseract with PyMuPDF PDF→image rendering |

---

## 8. LLM Adapter Contract

```python
# llm/base.py

@runtime_checkable
class LLMAdapter(Protocol):
    def complete(self, prompt: str) -> str: ...
    def is_available(self) -> bool: ...
```

| Adapter | Module | Notes |
|---|---|---|
| `OllamaAdapter` | `llm/ollama_adapter.py` | Full implementation with `_CircuitBreaker` |
| `OpenAIAdapter` | `llm/openai_adapter.py` | Stub — set `LLM_PROVIDER=openai` + `OPENAI_API_KEY` |
| `AzureOpenAIAdapter` | `llm/azure_openai_adapter.py` | Stub — Azure endpoint + deployment config |
| `AnthropicAdapter` | `llm/anthropic_adapter.py` | Stub — set `LLM_PROVIDER=anthropic` + `ANTHROPIC_API_KEY` |

---

## 9. Status Aggregation

After all pipeline stages complete, `ExtractionPipeline` calls `_aggregate_status`:

```python
def _aggregate_status(reports: list[ValidationReport]) -> Literal["PASS", "DEGRADED", "REJECTED"]:
    if any(r.status == "BLOCK" for r in reports):
        return "REJECTED"
    if any(r.status == "WARN" for r in reports):
        return "DEGRADED"
    return "PASS"
```

`ERROR` is set by the pipeline runner only when an unhandled exception escapes the entire `_run_stages()` call.

---

## 10. Output File Naming

```python
# workflow/extraction_pipeline.py

def _build_cv_filename(full_name: str | None, document_id: str) -> str:
    # example: "cv_john_doe_3b4b7594.json"
    name_parts = (full_name or "unknown").strip().split()
    first = slugify(name_parts[0])
    last  = slugify(name_parts[-1]) if len(name_parts) >= 2 else ""
    short_id = document_id.replace("-", "")[:8]
    slug = f"{first}_{last}" if last else first
    return f"cv_{slug}_{short_id}.json"

def _build_technical_filename(title: str | None, document_id: str) -> str:
    # example: "tech_microservices_architecture_3b4b7594.json"
    slug = slugify((title or "document")[:40]) or "document"
    short_id = document_id.replace("-", "")[:8]
    return f"tech_{slug}_{short_id}.json"
```

Written to `settings.output_dir`. For CV documents, the filename is sent to the backend as `jsonFile`. For TECHNICAL documents, the structured `KnowledgeExtraction` payload is posted directly to `/api/v1/knowledge/ingest` — the file is a local audit copy only.
