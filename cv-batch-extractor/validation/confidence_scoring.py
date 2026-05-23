from __future__ import annotations

import logging

from domain.models import PipelineContext, ValidationReport

logger = logging.getLogger(__name__)


class ConfidenceScorer:
    """
    GAP-003 fix — covers all four spec cases:
      HIGH    → PASS
      MEDIUM  → WARN
      LOW     → WARN
      null    → WARN ("field absent from LLM response")
    Never BLOCKs.
    """

    def validate(self, ctx: PipelineContext) -> ValidationReport:
        if ctx.cv_data is None:
            return ValidationReport(
                validator="confidence",
                status="WARN",
                reason="confidence: cv_data unavailable, confidence cannot be assessed",
            )

        level = ctx.cv_data.confidenceOverall
        low_fields = ctx.cv_data.lowConfidenceFields

        if level is None:
            return ValidationReport(
                validator="confidence",
                status="WARN",
                reason="confidence: field absent from LLM response",
                metadata={"confidenceOverall": None},
            )

        if level == "LOW":
            return ValidationReport(
                validator="confidence",
                status="WARN",
                reason="confidence: LOW — not enough data to reliably extract this CV",
                metadata={"confidenceOverall": "LOW", "lowConfidenceFields": low_fields},
            )

        if level == "MEDIUM":
            return ValidationReport(
                validator="confidence",
                status="WARN",
                reason="confidence: MEDIUM — partial data, recommend manual review",
                metadata={"confidenceOverall": "MEDIUM", "lowConfidenceFields": low_fields},
            )

        return ValidationReport(
            validator="confidence",
            status="PASS",
            metadata={
                "confidenceOverall": "HIGH",
                "missingFields": ctx.cv_data.missingFields,
            },
        )
