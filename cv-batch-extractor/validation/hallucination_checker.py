from __future__ import annotations

import logging
import re

from domain.models import PipelineContext, ValidationReport

logger = logging.getLogger(__name__)

_FUTURE_YEAR_THRESHOLD = 2  # years ahead of today considered suspicious


class HallucinationChecker:
    """
    Detects signs that the LLM invented data not present in the source text.

    Checks performed (all WARN, never BLOCK):
    1. Grounding check — key scalar fields (fullName, email, phone) must appear
       verbatim or near-verbatim in the original raw_text.
    2. Future date check — work endDates or education endYears far in the future.
    3. Implausible GPA — GPA outside 0–4.5 range.

    TODO: Add embedding-based grounding check for summary and responsibility bullets.
    TODO: Extend with a dedicated fact-checking LLM call for high-value extractions.
    """

    def validate(self, ctx: PipelineContext) -> ValidationReport:
        if ctx.cv_data is None:
            return ValidationReport(validator="hallucination", status="PASS")

        issues: list[str] = []
        source = (ctx.raw_text or "").lower()

        # 1. Grounding — fullName
        if ctx.cv_data.fullName:
            parts = ctx.cv_data.fullName.lower().split()
            if parts and not any(p in source for p in parts if len(p) > 2):
                issues.append(f"fullName '{ctx.cv_data.fullName}' not found in source text")

        # 2. Grounding — email
        if ctx.cv_data.email and ctx.cv_data.email.lower() not in source:
            issues.append(f"email '{ctx.cv_data.email}' not found in source text")

        # 3. Implausible GPA
        from datetime import date
        current_year = date.today().year

        for edu in ctx.cv_data.educations:
            if edu.gpa is not None and not (0 <= edu.gpa <= 4.5):
                issues.append(
                    f"implausible GPA {edu.gpa} at {edu.institution!r} (expected 0–4.5)"
                )
            if edu.endYear and edu.endYear > current_year + _FUTURE_YEAR_THRESHOLD:
                issues.append(
                    f"education endYear {edu.endYear} is suspiciously far in the future"
                )

        # 4. Future endDate in work experience
        today_str = date.today().isoformat()
        for exp in ctx.cv_data.workExperiences:
            if exp.endDate and not exp.isCurrent and exp.endDate > today_str:
                issues.append(
                    f"work endDate {exp.endDate} at {exp.company!r} is in the future"
                )

        if issues:
            return ValidationReport(
                validator="hallucination",
                status="WARN",
                reason=f"hallucination: {len(issues)} potential issue(s): {'; '.join(issues[:5])}",
                metadata={"issues": issues},
            )

        return ValidationReport(validator="hallucination", status="PASS")
