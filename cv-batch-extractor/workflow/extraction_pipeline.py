from __future__ import annotations

import json
import logging
import os
import re
from pathlib import Path
from typing import Literal

from config.settings import settings
from domain.models import PipelineContext, ProcessingResult, ValidationReport
from monitoring.metrics.collector import metrics
from monitoring.tracing.tracer import tracer

logger = logging.getLogger(__name__)


def _is_blocked(ctx: PipelineContext) -> bool:
    return any(r.status == "BLOCK" for r in ctx.reports)


def _aggregate_status(
    reports: list[ValidationReport],
) -> Literal["PASS", "DEGRADED", "REJECTED"]:
    if any(r.status == "BLOCK" for r in reports):
        return "REJECTED"
    if any(r.status == "WARN" for r in reports):
        return "DEGRADED"
    return "PASS"


def _slugify(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.lower()).strip("_")


def _build_filename(full_name: str | None, document_id: str) -> str:
    name = (full_name or "unknown").strip().split()
    first = _slugify(name[0]) if name else "unknown"
    last = _slugify(name[-1]) if len(name) >= 2 else ""
    short_id = document_id.replace("-", "")[:8]
    slug = f"{first}_{last}" if last else first
    return f"cv_{slug}_{short_id}.json"


def _write_json(ctx: PipelineContext) -> str | None:
    if ctx.cv_data is None:
        return None
    filename = _build_filename(ctx.cv_data.fullName, ctx.document_id)
    out_path = Path(settings.output_dir) / filename
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(
        json.dumps(ctx.cv_data.model_dump(), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    logger.info("Saved extraction to %s", out_path)
    return filename


class ExtractionPipeline:
    """
    Orchestrates the full document extraction workflow:

      Classify → Preprocess → OCR → Extract → Chunk → LLM → Validate → Normalise

    All components are created lazily from factories so the class itself
    has no hard dependency on any specific adapter at import time.
    """

    def __init__(self) -> None:
        self._classifier = None
        self._preprocessor = None
        self._ocr = None
        self._layout_analyzer = None
        self._table_extractor = None
        self._entity_extractor = None
        self._meta_extractor = None
        self._chunker = None
        self._llm = None
        self._schema_validator = None
        self._confidence_scorer = None
        self._hallucination_checker = None
        self._normalizer = None
        self._enricher = None

    def _ensure_built(self) -> None:
        if self._llm is not None:
            return

        from ingestion.document_classifier.classifier import DocumentClassifier
        from preprocessing.pipeline import PreprocessingPipeline
        from ocr import ocr_factory
        from extraction.layout_analysis.analyzer import LayoutAnalyzer
        from extraction.table_extraction.extractor import TableExtractor
        from extraction.entity_extraction.extractor import EntityExtractor
        from extraction.metadata_extraction.extractor import MetadataExtractor
        from chunking.text_chunker import TextChunker
        from llm import llm_factory
        from validation.schema_validator import SchemaValidator
        from validation.confidence_scoring import ConfidenceScorer
        from validation.hallucination_checker import HallucinationChecker
        from postprocessing.normalization.normalizer import Normalizer
        from postprocessing.enrichment.enricher import Enricher

        self._classifier = DocumentClassifier()
        self._preprocessor = PreprocessingPipeline()
        self._ocr = ocr_factory.create(settings.ocr_engine)
        self._layout_analyzer = LayoutAnalyzer()
        self._table_extractor = TableExtractor()
        self._entity_extractor = EntityExtractor()
        self._meta_extractor = MetadataExtractor()
        self._chunker = TextChunker(settings.chunk_size, settings.chunk_overlap)
        self._llm = llm_factory.create(settings.llm_provider)
        self._schema_validator = SchemaValidator()
        self._confidence_scorer = ConfidenceScorer()
        self._hallucination_checker = HallucinationChecker()
        self._normalizer = Normalizer()
        self._enricher = Enricher()

    # ── public ─────────────────────────────────────────────────────────────

    def run(self, document_id: str, category_id: str, file_path: str) -> ProcessingResult:
        self._ensure_built()
        ctx = PipelineContext(
            document_id=document_id,
            category_id=category_id,
            file_path=file_path,
        )

        import time
        t0 = time.monotonic()

        try:
            with tracer.start_span("extraction", attributes={"document_id": document_id}):
                self._run_stages(ctx)

            status = _aggregate_status(ctx.reports)
            output_file = _write_json(ctx) if status in ("PASS", "DEGRADED") else None

            elapsed = time.monotonic() - t0
            metrics.observe("document_processing_seconds", elapsed)
            metrics.inc(f"documents.{status.lower()}")

            logger.info(
                "Document %s finished status=%s warnings=%d elapsed=%.2fs",
                document_id, status,
                sum(1 for r in ctx.reports if r.status == "WARN"),
                elapsed,
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
            metrics.inc("documents.error")
            return ProcessingResult(
                document_id=document_id,
                category_id=category_id,
                status="ERROR",
                cv_data=None,
                output_file=None,
                reports=ctx.reports,
                error=str(exc),
            )

    # ── pipeline stages ────────────────────────────────────────────────────

    def _run_stages(self, ctx: PipelineContext) -> None:
        # 1 — classify
        ctx.document_type = self._classifier.classify(ctx.file_path)

        # 2 — validate file before heavy processing
        self._validate_file(ctx)
        if _is_blocked(ctx):
            return

        # 3 — preprocess
        ctx.preprocessed_path = self._preprocessor.process(
            ctx.file_path, ctx.document_type or "unknown"
        )

        # 4 — OCR
        import time
        t_ocr = time.monotonic()
        ocr_result = self._ocr.extract(ctx.preprocessed_path or ctx.file_path)
        metrics.observe("ocr_seconds", time.monotonic() - t_ocr)
        ctx.raw_text = ocr_result.text
        ctx.ocr_confidence = ocr_result.confidence

        # 5 — text input validation
        self._validate_text(ctx)
        if _is_blocked(ctx):
            return

        # 6 — layout + extraction
        ctx.layout = self._layout_analyzer.analyze(ctx.file_path).__dict__
        ctx.tables = self._table_extractor.extract(ctx.preprocessed_path or ctx.file_path)
        entities = self._entity_extractor.extract(ctx.raw_text or "")
        ctx.entities = entities.__dict__
        ctx.doc_metadata = self._meta_extractor.extract(ctx.file_path)

        # 7 — chunking (single-chunk for now; multi-chunk aggregation is a future TODO)
        ctx.chunks = self._chunker.chunk(ctx.prompt_text or ctx.raw_text or "")

        # 8 — LLM extraction
        if ctx.chunks:
            t_llm = time.monotonic()
            from llm.prompt_templates.cv_extraction import build as build_prompt
            prompt = build_prompt(ctx.chunks[0])
            ctx.llm_raw = self._llm.complete(prompt)
            metrics.observe("llm_seconds", time.monotonic() - t_llm)

        # 9 — output validation
        self._schema_validator.validate(ctx)
        if _is_blocked(ctx):
            return

        ctx.reports.append(self._confidence_scorer.validate(ctx))
        ctx.reports.append(self._hallucination_checker.validate(ctx))

        # 10 — normalise + enrich
        ctx.reports.append(self._normalizer.normalize(ctx))
        self._enricher.enrich(ctx)

    # ── file-level input validation ────────────────────────────────────────

    def _validate_file(self, ctx: PipelineContext) -> None:
        import os
        size_mb = os.path.getsize(ctx.file_path) / (1024 * 1024)
        if size_mb > settings.max_file_size_mb:
            ctx.reports.append(ValidationReport(
                validator="file_size",
                status="BLOCK",
                reason=f"file_size: {size_mb:.1f} MB exceeds limit of {settings.max_file_size_mb} MB",
            ))
            return

        try:
            import magic
            mime = magic.from_file(ctx.file_path, mime=True)
            if mime not in settings.allowed_mime_types:
                ctx.reports.append(ValidationReport(
                    validator="mime_type",
                    status="BLOCK",
                    reason=f"mime_type: {mime!r} is not a supported document type",
                ))
                return
        except ImportError:
            pass

        ctx.reports.append(ValidationReport(validator="file_size", status="PASS"))

    # ── text-level input validation ────────────────────────────────────────

    def _validate_text(self, ctx: PipelineContext) -> None:
        import re as _re

        text = ctx.raw_text or ""

        # length
        if len(text.strip()) < settings.min_text_chars:
            ctx.reports.append(ValidationReport(
                validator="text_length",
                status="BLOCK",
                reason=f"text_length: {len(text)} chars extracted — document may be empty or image-only",
            ))
            return

        if len(text) > settings.max_text_chars:
            ctx.raw_text = text[:settings.max_text_chars]
            ctx.reports.append(ValidationReport(
                validator="text_length",
                status="WARN",
                reason=f"text_length: truncated from {len(text):,} to {settings.max_text_chars:,} chars",
            ))
        else:
            ctx.reports.append(ValidationReport(validator="text_length", status="PASS"))

        text = ctx.raw_text or ""

        # quality
        word_count = len(text.split())
        printable_ratio = sum(c.isprintable() for c in text) / max(len(text), 1)
        quality_issues: list[str] = []

        if word_count < settings.min_word_count:
            quality_issues.append(f"low word count ({word_count} words, min {settings.min_word_count})")
        if printable_ratio < settings.min_printable_ratio:
            quality_issues.append(
                f"low printable ratio ({printable_ratio:.2f}, min {settings.min_printable_ratio})"
            )

        ctx.reports.append(ValidationReport(
            validator="text_quality",
            status="WARN" if quality_issues else "PASS",
            reason=f"text_quality: {'; '.join(quality_issues)}" if quality_issues else None,
        ))

        # injection
        import re as _re2
        patterns = [_re2.compile(p) for p in settings.injection_patterns]
        sanitized = text
        matches_found = 0
        for pattern in patterns:
            sanitized, count = pattern.subn("[REDACTED]", sanitized)
            matches_found += count

        ctx.prompt_text = sanitized
        if matches_found:
            logger.warning("Injection patterns detected (%d) in document %s", matches_found, ctx.document_id)
            ctx.reports.append(ValidationReport(
                validator="injection",
                status="WARN",
                reason=f"injection: {matches_found} potential prompt-override pattern(s) detected and redacted",
                metadata={"match_count": matches_found},
            ))
        else:
            ctx.reports.append(ValidationReport(validator="injection", status="PASS"))
