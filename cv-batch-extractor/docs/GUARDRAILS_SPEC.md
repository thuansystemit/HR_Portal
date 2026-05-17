# CV Batch Extractor — Guardrails Specification

Each guard is a class that implements one method:

```python
def run(self, ctx: PipelineContext) -> GuardrailReport
```

Guards may mutate `ctx` (e.g. truncate text, sanitize strings) before returning their report.
A `BLOCK` report causes the pipeline to skip all subsequent guards and jump to `ResultBuilder`.

---

## Input Pipeline Guards (Pre-LLM)

### 1. FileSizeGuard

**Purpose:** Prevent oversized files from reaching the text extractor or LLM.

| Field | Value |
|---|---|
| Module | `guardrails/input/file_size.py` |
| Config key | `max_file_size_mb` (default: `20`) |
| Reads from ctx | `ctx.file_path` |
| Writes to ctx | nothing |

**Logic:**
1. `os.path.getsize(ctx.file_path)`
2. If size > `max_file_size_mb * 1024 * 1024` → return `BLOCK`
3. Otherwise → return `PASS`

**Report examples:**
```
BLOCK  "file_size: 45.2 MB exceeds limit of 20 MB"
PASS   None
```

---

### 2. MimeTypeGuard

**Purpose:** Validate the actual file type via magic bytes, not just the file extension.

| Field | Value |
|---|---|
| Module | `guardrails/input/mime_type.py` |
| Dependency | `python-magic` |
| Config key | `allowed_mime_types` (default: see below) |
| Reads from ctx | `ctx.file_path` |
| Writes to ctx | nothing |

**Allowed MIME types (default):**
```python
{
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",  # .docx
    "application/msword",                                                         # .doc
    "image/jpeg",
    "image/png",
    "image/tiff",
    "image/bmp",
}
```

**Logic:**
1. `magic.from_file(ctx.file_path, mime=True)`
2. If detected MIME not in allowed set → return `BLOCK`
3. Otherwise → return `PASS`

**Report examples:**
```
BLOCK  "mime_type: detected application/zip is not a supported document type"
PASS   None
```

---

### 3. TextExtractor (Transformer)

**Purpose:** Extract raw text from the document and populate `ctx.raw_text`. Not a guard — always produces `PASS`. If extraction raises an exception, it is caught and returns `BLOCK` with the error message.

| Field | Value |
|---|---|
| Module | `guardrails/input/text_extractor.py` |
| Reads from ctx | `ctx.file_path` |
| Writes to ctx | `ctx.raw_text` |

**Logic:**
1. Call `parsers.extract_text(ctx.file_path)`
2. On success → set `ctx.raw_text = text`, return `PASS`
3. On exception → return `BLOCK` with exception message

---

### 4. TextLengthGuard

**Purpose:** Ensure the extracted text is non-empty and not so large it overflows the LLM context window. Truncates oversized text rather than blocking it.

| Field | Value |
|---|---|
| Module | `guardrails/input/text_length.py` |
| Config keys | `min_text_chars` (default: `50`), `max_text_chars` (default: `40000`) |
| Reads from ctx | `ctx.raw_text` |
| Writes to ctx | `ctx.raw_text` (truncated if over max) |

**Logic:**
1. If `len(ctx.raw_text.strip()) < min_text_chars` → return `BLOCK` — nothing to extract
2. If `len(ctx.raw_text) > max_text_chars`:
   - Truncate `ctx.raw_text` to `max_text_chars` characters
   - Return `WARN`
3. Otherwise → return `PASS`

**Report examples:**
```
BLOCK  "text_length: 0 characters extracted — document may be empty or image-only"
WARN   "text_length: truncated from 87,432 to 40,000 characters"
PASS   None
```

---

### 5. TextQualityGuard

**Purpose:** Detect garbled OCR output or corrupt document text that would produce low-quality extractions before even calling the LLM.

| Field | Value |
|---|---|
| Module | `guardrails/input/text_quality.py` |
| Config keys | `min_word_count` (default: `30`), `min_printable_ratio` (default: `0.85`) |
| Reads from ctx | `ctx.raw_text` |
| Writes to ctx | nothing |

**Logic:**
1. Count words: `len(ctx.raw_text.split())`
2. Compute printable ratio: `sum(c.isprintable() for c in ctx.raw_text) / len(ctx.raw_text)`
3. If word count < `min_word_count` → return `WARN`
4. If printable ratio < `min_printable_ratio` → return `WARN`
5. Otherwise → return `PASS`

Multiple WARNs are merged into a single report with both reasons listed.

**Report examples:**
```
WARN   "text_quality: low word count (18 words, min 30)"
WARN   "text_quality: low printable character ratio (0.71, min 0.85) — possible garbled OCR"
PASS   None
```

---

### 6. InjectionGuard

**Purpose:** Detect and sanitize CV text that contains LLM prompt-override attempts. Hard blocking would let adversarial files DoS the pipeline, so this guard always WARNs and sanitizes — never BLOCKs.

| Field | Value |
|---|---|
| Module | `guardrails/input/injection.py` |
| Config key | `injection_patterns` (list of regex strings, see defaults below) |
| Reads from ctx | `ctx.raw_text` |
| Writes to ctx | `ctx.prompt_text` (sanitized copy; `ctx.raw_text` is preserved as-is for audit) |

**Default injection patterns:**
```python
[
    r"(?i)ignore\s+(all\s+)?previous\s+instructions",
    r"(?i)disregard\s+(the\s+)?(above|previous|prior)",
    r"(?i)you\s+are\s+now\s+a",
    r"(?i)act\s+as\s+(a\s+)?(?!recruiter|hiring)",   # allow "act as a recruiter"
    r"(?i)system\s*prompt",
    r"(?i)jailbreak",
    r"(?i)<\s*/?(?:system|instructions?|prompt)\s*>",
]
```

**Logic:**
1. Scan `ctx.raw_text` for all patterns
2. If no matches → copy `ctx.raw_text` to `ctx.prompt_text` unchanged, return `PASS`
3. If matches found:
   - Replace each match with `[REDACTED]` → store in `ctx.prompt_text`
   - Log the match positions for the audit trail
   - Return `WARN` with count of matches and pattern names

**Report examples:**
```
WARN   "injection: 2 potential prompt-override pattern(s) detected and redacted"
PASS   None
```

---

## Output Pipeline Guards (Post-LLM)

### 7. JsonParseGuard

**Purpose:** Parse the raw LLM string output into a Python dict. Handles markdown fences and leading/trailing prose.

| Field | Value |
|---|---|
| Module | `guardrails/output/json_parse.py` |
| Reads from ctx | `ctx.llm_raw` |
| Writes to ctx | `ctx.raw_dict` (`dict`) |

**Logic:** Existing `_extract_json` logic from `extractor.py`, moved here and wrapped as a guard.
1. Strip markdown fences
2. Attempt `json.loads`
3. If fails: extract outermost `{...}` block and retry
4. On success → set `ctx.raw_dict`, return `PASS`
5. On failure → return `BLOCK`

**Report examples:**
```
BLOCK  "json_parse: LLM output could not be parsed as JSON after all attempts"
PASS   None
```

---

### 8. SchemaGuard

**Purpose:** Validate `ctx.raw_dict` against the `CvExtraction` Pydantic model. Distinguishes hard violations (structure is broken) from soft violations (optional fields have bad format).

| Field | Value |
|---|---|
| Module | `guardrails/output/schema.py` |
| Reads from ctx | `ctx.raw_dict` |
| Writes to ctx | `ctx.cv_data` (`CvExtraction` instance) |

**Hard violations → BLOCK:**
- Top-level object is not a dict
- `workExperiences`, `educations`, `languages`, `certifications` exist but are not lists

**Soft violations → WARN:**
- Unknown extra fields present (LLM hallucinated a key)
- Expected list field is `null` instead of `[]`
- Nested object missing a required sub-field

**Logic:**
1. Attempt `CvExtraction.model_validate(ctx.raw_dict)` with `strict=False`
2. On clean success → set `ctx.cv_data`, return `PASS`
3. On `ValidationError`:
   - Classify each error as hard or soft
   - If any hard → return `BLOCK` with summary
   - If only soft → coerce what we can, set `ctx.cv_data`, return `WARN` with list of issues

**Report examples:**
```
BLOCK  "schema: workExperiences is not a list (got str)"
WARN   "schema: 2 soft violation(s): [extra field 'hobbies', null instead of [] for 'publications']"
PASS   None
```

---

### 9. SemanticGuard

**Purpose:** Validate data-level rules that Pydantic types cannot enforce — date ordering, ISO standard codes, email format, and internal consistency.

| Field | Value |
|---|---|
| Module | `guardrails/output/semantic.py` |
| Reads from ctx | `ctx.cv_data` |
| Writes to ctx | nothing (flags issues in report only) |

**Rules checked:**

| Check | Rule |
|---|---|
| Date ordering | `startDate < endDate` for all work/education entries |
| Future start date | `startDate` ≤ today (warn if in the future) |
| Country code | Each `country` must be valid ISO 3166-1 alpha-2 |
| Language code | `rawLanguage` must be valid ISO 639-1 |
| Email format | `email` must match RFC 5322 simplified regex |
| Phone characters | `phone` contains only digits, spaces, `+`, `-`, `(`, `)` |
| HIGH confidence null check | If `confidenceOverall == HIGH`: `fullName` must not be null |
| Current role consistency | If `isCurrent == true`: `endDate` must be null |

All checks are `WARN` only — semantic issues never BLOCK.

**Report examples:**
```
WARN   "semantic: 3 issue(s): [work[1].endDate before startDate, country 'XX' invalid ISO 3166-1, email format invalid]"
PASS   None
```

---

### 10. ConfidenceGuard

**Purpose:** Annotate the result with the LLM's self-reported confidence. Never blocks — the backend owns the business decision.

| Field | Value |
|---|---|
| Module | `guardrails/output/confidence.py` |
| Reads from ctx | `ctx.cv_data.confidenceOverall` |
| Writes to ctx | nothing |

**Logic:**

| `confidenceOverall` | Report |
|---|---|
| `HIGH` | `PASS` |
| `MEDIUM` | `WARN` — "confidence: MEDIUM — partial data, recommend manual review" |
| `LOW` | `WARN` — "confidence: LOW — not enough data to reliably extract this CV" |
| missing / null | `WARN` — "confidence: field absent from LLM response" |

---

### 11. SanitizeGuard (Transformer)

**Purpose:** Clean all string fields before writing to disk. Always returns `PASS`.

| Field | Value |
|---|---|
| Module | `guardrails/output/sanitize.py` |
| Reads from ctx | `ctx.cv_data` |
| Writes to ctx | `ctx.cv_data` (mutated in place) |

**Transformations applied to every string field recursively:**
1. Strip leading/trailing whitespace
2. Remove ASCII control characters (`\x00`–`\x1f`, excluding `\t \n \r`)
3. Collapse runs of 3+ newlines to 2
4. Cap field lengths (configurable, defaults below):

| Field | Max chars |
|---|---|
| `fullName` | 200 |
| `email` | 320 |
| `summary` | 5000 |
| Any `responsibilities[]` item | 1000 |
| Any `achievements[]` item | 1000 |
| All other string fields | 500 |

Fields that exceed their cap are truncated; the field name is added to `lowConfidenceFields` if not already present.

---

## Guard Registration Order

Guards must run in this exact order. The pipeline short-circuits on the first `BLOCK` — subsequent guards are skipped.

```python
INPUT_GUARDS = [
    FileSizeGuard,
    MimeTypeGuard,
    TextExtractor,
    TextLengthGuard,
    TextQualityGuard,
    InjectionGuard,
]

OUTPUT_GUARDS = [
    JsonParseGuard,
    SchemaGuard,
    SemanticGuard,
    ConfidenceGuard,
    SanitizeGuard,
]
```

---

## Status Aggregation Rules

After all guards have run, the final `ProcessingResult.status` is determined:

```python
def aggregate_status(reports: list[GuardrailReport]) -> str:
    if any(r.status == "BLOCK" for r in reports):
        return "REJECTED"
    if any(r.status == "WARN" for r in reports):
        return "DEGRADED"
    return "PASS"
```

`ERROR` is set by the pipeline runner only when an unhandled exception escapes a guard.
