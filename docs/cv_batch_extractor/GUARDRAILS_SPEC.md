# CV Batch Extractor — Validation & Guardrails Specification

Validation is distributed across pipeline stages rather than a single guard chain. Each validator appends a `ValidationReport` to `PipelineContext.reports`. A `BLOCK` report short-circuits the pipeline — all subsequent stages are skipped and the document goes to `REJECTED`.

```python
@dataclass
class ValidationReport:
    validator: str                               # stage name
    status: Literal["PASS", "WARN", "BLOCK"]
    reason: str | None = None
    metadata: dict = field(default_factory=dict)
```

---

## Stage 2 — File Input Validation

Implemented inline in `ExtractionPipeline._validate_file()`.

### FileSizeValidator

**Purpose:** Prevent oversized files from reaching OCR or the LLM.

| Field | Value |
|---|---|
| `validator` name | `"file_size"` |
| Config key | `max_file_size_mb` (default: `20.0`) |
| Reads from ctx | `ctx.file_path` |
| Writes to ctx | nothing |

**Logic:**
1. `os.path.getsize(ctx.file_path) / (1024 * 1024)`
2. If `size_mb > max_file_size_mb` → `BLOCK`
3. Otherwise → `PASS`

**Report examples:**
```
BLOCK  "file_size: 45.2 MB exceeds limit of 20 MB"
PASS   None
```

---

### MimeTypeValidator

**Purpose:** Validate the actual file type via magic bytes, not the file extension.

| Field | Value |
|---|---|
| `validator` name | `"mime_type"` |
| Dependency | `python-magic` (gracefully skipped on `ImportError`) |
| Config key | `allowed_mime_types` (list, see `config/settings.py`) |
| Reads from ctx | `ctx.file_path` |
| Writes to ctx | nothing |

**Allowed MIME types (default):**
```
application/pdf
application/vnd.openxmlformats-officedocument.wordprocessingml.document  (.docx)
application/msword                                                          (.doc)
image/jpeg  image/png  image/tiff  image/bmp
```

**Logic:**
1. `magic.from_file(ctx.file_path, mime=True)`
2. If detected MIME not in `allowed_mime_types` → `BLOCK`
3. Otherwise → `PASS`

**Report examples:**
```
BLOCK  "mime_type: 'application/zip' is not a supported document type"
PASS   None
```

---

## Stage 5 — Text Input Validation

Implemented inline in `ExtractionPipeline._validate_text()`.

### TextLengthValidator

**Purpose:** Ensure extracted text is non-empty and within LLM context window limits. Truncates rather than blocks on oversized text.

| Field | Value |
|---|---|
| `validator` name | `"text_length"` |
| Config keys | `min_text_chars` (default: `50`), `max_text_chars` (default: `40000`) |
| Reads from ctx | `ctx.raw_text` |
| Writes to ctx | `ctx.raw_text` (truncated if over max) |

**Logic:**
1. If `len(raw_text.strip()) < min_text_chars` → `BLOCK`
2. If `len(raw_text) > max_text_chars` → truncate `ctx.raw_text`, emit `WARN`
3. Otherwise → `PASS`

**Report examples:**
```
BLOCK  "text_length: 12 chars extracted — document may be empty or image-only"
WARN   "text_length: truncated from 87,432 to 40,000 chars"
PASS   None
```

---

### TextQualityValidator

**Purpose:** Detect garbled OCR or corrupt document text before calling the LLM.

| Field | Value |
|---|---|
| `validator` name | `"text_quality"` |
| Config keys | `min_word_count` (default: `30`), `min_printable_ratio` (default: `0.85`) |
| Reads from ctx | `ctx.raw_text` |
| Writes to ctx | nothing |

**Logic:**
1. `word_count = len(raw_text.split())`
2. `printable_ratio = sum(c.isprintable() for c in raw_text) / max(len(raw_text), 1)`
3. Collect any failures into `quality_issues`
4. If `quality_issues` → `WARN` with joined reason
5. Otherwise → `PASS`

**Report examples:**
```
WARN   "text_quality: low word count (18 words, min 30); low printable ratio (0.71, min 0.85)"
PASS   None
```

---

### InjectionValidator

**Purpose:** Detect and sanitize CV text containing LLM prompt-override attempts. Always `WARN` — never `BLOCK` (hard-blocking would allow adversarial files to DoS the pipeline).

| Field | Value |
|---|---|
| `validator` name | `"injection"` |
| Config key | `injection_patterns` (list of regex strings) |
| Reads from ctx | `ctx.raw_text` |
| Writes to ctx | `ctx.prompt_text` (sanitized copy; `ctx.raw_text` preserved for audit) |

**Default injection patterns (7):**
```python
r"(?i)ignore\s+(all\s+)?previous\s+instructions"
r"(?i)disregard\s+(the\s+)?(above|previous|prior)"
r"(?i)you\s+are\s+now\s+a"
r"(?i)act\s+as\s+a\s+(?!recruiter|hiring)"   # allow "act as a recruiter"
r"(?i)system\s*prompt"
r"(?i)jailbreak"
r"(?i)<\s*/?(?:system|instructions?|prompt)\s*>"
```

**Logic:**
1. For each compiled pattern → `re.subn("[REDACTED]", text)`
2. Count total substitutions across all patterns
3. `ctx.prompt_text = sanitized`
4. If matches found → `WARN` with count; otherwise `PASS`

**Report examples:**
```
WARN   "injection: 2 potential prompt-override pattern(s) detected and redacted"
PASS   None
```

---

## Stage 9 — Output Validation

### SchemaValidator

Module: `validation/schema_validator.py`

Two internal steps run in sequence:

**Step 1 — JSON Parse**

| `validator` name | `"json_parse"` |
|---|---|
| Reads from ctx | `ctx.llm_raw` |
| Writes to ctx | `ctx.raw_dict` |

Logic:
1. Strip markdown fences (` ```json ` … ` ``` `)
2. `json.loads(llm_raw)`
3. If fails: extract outermost `{…}` block and retry
4. On success → set `ctx.raw_dict`, `PASS`
5. On failure → `BLOCK`

```
BLOCK  "json_parse: LLM output could not be parsed as JSON after all attempts"
PASS   None
```

**Step 2 — Pydantic Model Validation**

| `validator` name | `"schema"` |
|---|---|
| Reads from ctx | `ctx.raw_dict` |
| Writes to ctx | `ctx.cv_data` (`CvExtraction` instance) |

Hard violations → `BLOCK`:
- Top-level value is not a dict
- List fields (`workExperiences`, `educations`, `languages`, `certifications`) exist but are not lists

Soft violations → `WARN`:
- Extra keys present (LLM hallucinated a field)
- List field is `null` instead of `[]`
- Nested object missing an optional sub-field

```
BLOCK  "schema: workExperiences is not a list (got str)"
WARN   "schema: 2 soft violation(s): [extra field 'hobbies', null for 'publications']"
PASS   None
```

---

### ConfidenceScorer

Module: `validation/confidence_scoring.py`

**Purpose:** Annotate the result with the LLM's self-reported confidence. Never blocks.

| `validator` name | `"confidence"` |
|---|---|
| Reads from ctx | `ctx.cv_data.confidenceOverall` |
| Writes to ctx | nothing |

| `confidenceOverall` | Status | Reason |
|---|---|---|
| `"HIGH"` | `PASS` | — |
| `"MEDIUM"` | `WARN` | `"confidence: MEDIUM — partial data, recommend manual review"` |
| `"LOW"` | `WARN` | `"confidence: LOW — not enough data to reliably extract this CV"` |
| `None` / absent | `WARN` | `"confidence: field absent from LLM response"` |

---

### HallucinationChecker

Module: `validation/hallucination_checker.py`

**Purpose:** Cross-validate LLM output against source text to flag likely hallucinations.

| `validator` name | `"hallucination"` |
|---|---|
| Reads from ctx | `ctx.cv_data`, `ctx.raw_text` |
| Writes to ctx | nothing |

Checks performed (all `WARN`):

| Check | Rule |
|---|---|
| Name grounding | `fullName` tokens must appear somewhere in `raw_text` |
| Email grounding | `email` must appear verbatim in `raw_text` |
| GPA plausibility | `gpa` must be in range `[0.0, 4.0]` |
| Future end dates | Work/education `endDate` must not be after today (unless `isCurrent`) |

---

## Stage 10 — Post-Processing

### Normalizer

Module: `postprocessing/normalization/normalizer.py`

**Purpose:** Clean all string fields before writing to disk. Always returns `PASS`.

| `validator` name | `"normalizer"` |
|---|---|
| Reads from ctx | `ctx.cv_data` |
| Writes to ctx | `ctx.cv_data` (mutated in place) |

**Transformations applied recursively to every string field:**
1. Strip leading/trailing whitespace
2. Remove ASCII control characters (`\x00`–`\x1f`, excluding `\t`, `\n`, `\r`)
3. Collapse runs of 3+ newlines to 2 newlines

**Field-specific length caps (configurable via settings):**

| Field | Config key | Default cap |
|---|---|---|
| `fullName` | `sanitize_max_full_name` | 200 chars |
| `email` | `sanitize_max_email` | 320 chars |
| `summary` | `sanitize_max_summary` | 5,000 chars |
| List items (responsibilities, achievements, etc.) | `sanitize_max_list_item` | 1,000 chars |
| All other string fields | `sanitize_max_default` | 500 chars |

Fields truncated beyond their cap have their name appended to `cv_data.lowConfidenceFields`.

---

## Status Aggregation

After all stages, `ExtractionPipeline` aggregates all `ValidationReport` statuses:

```python
def _aggregate_status(reports: list[ValidationReport]) -> str:
    if any(r.status == "BLOCK" for r in reports):
        return "REJECTED"
    if any(r.status == "WARN" for r in reports):
        return "DEGRADED"
    return "PASS"
```

`ERROR` is set only when an unhandled exception escapes the entire `_run_stages()` call.

---

## Validation Execution Order

```
Stage 2:  FileSizeValidator
          MimeTypeValidator
              ── BLOCK? → dead-letter

Stage 5:  TextLengthValidator
          TextQualityValidator
          InjectionValidator
              ── BLOCK? → dead-letter

Stage 9:  SchemaValidator
            ├─ json_parse
            └─ schema
          ConfidenceScorer
          HallucinationChecker
              ── BLOCK? → dead-letter

Stage 10: Normalizer  (always PASS)
          Enricher    (always PASS, stub)
```

Workers also short-circuit before Stage 1 if the `BoundedSemaphore` is exhausted:

```
Queue full → ValidationReport(validator="worker_pool", status="BLOCK",
             reason="worker_pool: queue full, document could not be scheduled")
           → dead-letter + notify backend immediately
```
