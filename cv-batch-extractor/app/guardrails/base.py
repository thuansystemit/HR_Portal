from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Literal, Protocol, runtime_checkable

from app.domain.cv_schema import CvExtraction
from app.domain.invoice_schema import InvoiceExtraction

if TYPE_CHECKING:
    from app.chunking import Chunk

logger = logging.getLogger(__name__)


@dataclass
class GuardrailReport:
    guard: str
    status: Literal["PASS", "WARN", "BLOCK"]
    reason: str | None = None
    metadata: dict = field(default_factory=dict)


@dataclass
class PipelineContext:
    document_id: str
    category_id: str
    file_path: str
    document_type: str = "CV"
    raw_text: str | None = None
    prompt_text: str | None = None
    llm_raw: str | None = None
    raw_dict: dict | None = None
    cv_data: CvExtraction | None = None
    invoice_data: InvoiceExtraction | None = None
    chunks: list[Chunk] = field(default_factory=list)
    reports: list[GuardrailReport] = field(default_factory=list)


@runtime_checkable
class Guard(Protocol):
    def run(self, ctx: PipelineContext) -> GuardrailReport:
        ...


class GuardrailPipeline:
    def __init__(self, guards: list[Guard]) -> None:
        self._guards = guards

    def run(self, ctx: PipelineContext) -> list[GuardrailReport]:
        for guard in self._guards:
            try:
                report = guard.run(ctx)
            except Exception as exc:
                name = type(guard).__name__
                logger.exception("Guard %s raised an unexpected exception", name)
                report = GuardrailReport(
                    guard=name,
                    status="BLOCK",
                    reason=f"{name}: unexpected error — {exc}",
                )
            ctx.reports.append(report)
            if report.status == "BLOCK":
                break
        return ctx.reports
