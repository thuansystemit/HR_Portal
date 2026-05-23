from __future__ import annotations

from dataclasses import dataclass, field
from typing import Literal

from domain.cv_schema import CvExtraction


@dataclass
class ValidationReport:
    validator: str
    status: Literal["PASS", "WARN", "BLOCK"]
    reason: str | None = None
    metadata: dict = field(default_factory=dict)


@dataclass
class PipelineContext:
    document_id: str
    category_id: str
    file_path: str

    # ingestion / classification
    document_type: str | None = None

    # preprocessing
    preprocessed_path: str | None = None

    # OCR output
    raw_text: str | None = None
    ocr_confidence: float | None = None

    # layout & extraction
    layout: dict | None = None
    tables: list[dict] = field(default_factory=list)
    key_values: dict = field(default_factory=dict)
    entities: dict = field(default_factory=dict)
    doc_metadata: dict = field(default_factory=dict)

    # injection sanitization — prompt_text is the LLM-safe copy
    prompt_text: str | None = None

    # chunking
    chunks: list[str] = field(default_factory=list)

    # LLM raw output
    llm_raw: str | None = None

    # validation / schema
    raw_dict: dict | None = None
    cv_data: CvExtraction | None = None

    # full audit trail
    reports: list[ValidationReport] = field(default_factory=list)


@dataclass
class ProcessingResult:
    document_id: str
    category_id: str
    status: Literal["PASS", "DEGRADED", "REJECTED", "ERROR"]
    cv_data: CvExtraction | None
    output_file: str | None
    reports: list[ValidationReport] = field(default_factory=list)
    error: str | None = None

    @property
    def warnings(self) -> list[str]:
        return [r.reason for r in self.reports if r.status == "WARN" and r.reason]
