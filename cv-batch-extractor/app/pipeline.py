import json
import logging
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Literal

from langchain_core.exceptions import OutputParserException

from app.backend_client import get_extraction_mode
from app.config import settings
from app.domain.cv_schema import CvExtraction
from app.guardrails.base import GuardrailPipeline, GuardrailReport, PipelineContext
from app.guardrails.input.file_size import FileSizeGuard
from app.guardrails.input.injection import InjectionGuard
from app.guardrails.input.liteparse_extractor import LiteParseTextExtractor
from app.guardrails.input.mime_type import MimeTypeGuard
from app.guardrails.input.text_extractor import TextExtractor
from app.guardrails.input.text_length import TextLengthGuard
from app.guardrails.input.text_quality import TextQualityGuard
from app.guardrails.output.confidence import ConfidenceGuard
from app.guardrails.output.sanitize import SanitizeGuard
from app.guardrails.output.semantic import SemanticGuard
from app.llm_chain import invoke_extraction

logger = logging.getLogger(__name__)


@dataclass
class ProcessingResult:
    document_id: str
    category_id: str
    status: Literal["PASS", "DEGRADED", "REJECTED", "ERROR"]
    cv_data: CvExtraction | None
    output_file: str | None
    reports: list[GuardrailReport] = field(default_factory=list)
    error: str | None = None

    @property
    def warnings(self) -> list[str]:
        return [r.reason for r in self.reports if r.status == "WARN" and r.reason]


_INPUT_PIPELINE = GuardrailPipeline([
    FileSizeGuard(),
    MimeTypeGuard(),
    TextExtractor(),
    TextLengthGuard(),
    TextQualityGuard(),
    InjectionGuard(),
])

_INPUT_PIPELINE_LITE = GuardrailPipeline([
    FileSizeGuard(),
    MimeTypeGuard(),
    LiteParseTextExtractor(),
    TextLengthGuard(),
    TextQualityGuard(),
    InjectionGuard(),
])

_OUTPUT_PIPELINE = GuardrailPipeline([
    SemanticGuard(),
    ConfidenceGuard(),
    SanitizeGuard(),
])


def _is_blocked(reports: list[GuardrailReport]) -> bool:
    return any(r.status == "BLOCK" for r in reports)


def _aggregate_status(
    reports: list[GuardrailReport],
) -> Literal["PASS", "DEGRADED", "REJECTED"]:
    if any(r.status == "BLOCK" for r in reports):
        return "REJECTED"
    if any(r.status == "WARN" for r in reports):
        return "DEGRADED"
    return "PASS"


def _slugify(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.lower()).strip("_")


def _build_output_filename(full_name: str | None, document_id: str) -> str:
    name = (full_name or "unknown").strip()
    parts = name.split()
    first = _slugify(parts[0]) if parts else "unknown"
    last = _slugify(parts[-1]) if len(parts) >= 2 else ""
    short_id = document_id.replace("-", "")[:8]
    slug = f"{first}_{last}" if last else first
    return f"cv_{slug}_{short_id}.json"


def _write_json(ctx: PipelineContext) -> str | None:
    if ctx.cv_data is None:
        return None
    filename = _build_output_filename(ctx.cv_data.fullName, ctx.document_id)
    output_path = Path(settings.output_dir) / filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(ctx.cv_data.model_dump(), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    logger.info("Saved extraction result to %s", output_path)
    return filename


class Pipeline:
    def run(self, document_id: str, category_id: str, file_path: str) -> ProcessingResult:
        ctx = PipelineContext(
            document_id=document_id,
            category_id=category_id,
            file_path=file_path,
        )
        try:
            use_llm = get_extraction_mode(category_id)
            logger.info(
                "Document %s  category=%s  mode=%s",
                document_id, category_id, "LLM" if use_llm else "LiteParse+LLM",
            )

            input_pipeline = _INPUT_PIPELINE if use_llm else _INPUT_PIPELINE_LITE
            input_pipeline.run(ctx)

            if not _is_blocked(ctx.reports):
                try:
                    ctx.cv_data = invoke_extraction(ctx.prompt_text or ctx.raw_text or "")
                    ctx.raw_dict = ctx.cv_data.model_dump()
                except OutputParserException as exc:
                    logger.error(
                        "Document %s: LLM output could not be parsed after self-correction: %s",
                        document_id,
                        exc,
                    )
                    ctx.reports.append(GuardrailReport(
                        guard="llm_extraction",
                        status="BLOCK",
                        reason=f"llm_extraction: could not parse output after self-correction — {exc}",
                    ))

                if not _is_blocked(ctx.reports):
                    _OUTPUT_PIPELINE.run(ctx)

            status = _aggregate_status(ctx.reports)
            output_file = _write_json(ctx) if status in ("PASS", "DEGRADED") else None

            logger.info(
                "Document %s finished with status=%s  warnings=%d",
                document_id,
                status,
                len([r for r in ctx.reports if r.status == "WARN"]),
            )
            return ProcessingResult(
                document_id=document_id,
                category_id=category_id,
                status=status,
                cv_data=ctx.cv_data,
                output_file=output_file,
                reports=ctx.reports,
            )
        except Exception as exc:
            logger.exception("Unhandled error for document %s", document_id)
            return ProcessingResult(
                document_id=document_id,
                category_id=category_id,
                status="ERROR",
                cv_data=None,
                output_file=None,
                reports=ctx.reports,
                error=str(exc),
            )
