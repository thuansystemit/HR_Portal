from __future__ import annotations

import json
import logging
import re

from domain.cv_schema import CvExtraction
from domain.models import PipelineContext, ValidationReport

logger = logging.getLogger(__name__)

_LIST_FIELDS = {
    "workExperiences", "educations", "languages", "certifications",
    "projects", "publications", "toolsAndFrameworks", "softSkills",
    "technicalSkills", "lowConfidenceFields", "missingFields",
}


class SchemaValidator:
    """
    Parses and validates LLM output.

    Step 1 — JsonParseGuard: parse raw LLM string → dict
    Step 2 — SchemaGuard: validate dict → CvExtraction pydantic model
    """

    def validate(self, ctx: PipelineContext) -> None:
        ctx.reports.append(self._parse_json(ctx))
        if ctx.raw_dict is not None:
            ctx.reports.append(self._validate_schema(ctx))

    # ── JSON parsing ───────────────────────────────────────────────────────

    def _parse_json(self, ctx: PipelineContext) -> ValidationReport:
        raw = ctx.llm_raw or ""
        raw = re.sub(r"^```(?:json)?\s*", "", raw.strip(), flags=re.IGNORECASE)
        raw = re.sub(r"\s*```$", "", raw.strip())

        try:
            ctx.raw_dict = json.loads(raw)
            return ValidationReport(validator="json_parse", status="PASS")
        except json.JSONDecodeError:
            pass

        match = re.search(r"\{.*\}", raw, re.DOTALL)
        if match:
            try:
                ctx.raw_dict = json.loads(match.group())
                return ValidationReport(validator="json_parse", status="PASS")
            except json.JSONDecodeError:
                pass

        return ValidationReport(
            validator="json_parse",
            status="BLOCK",
            reason="json_parse: LLM output could not be parsed as JSON",
        )

    # ── schema validation ──────────────────────────────────────────────────

    def _validate_schema(self, ctx: PipelineContext) -> ValidationReport:
        from pydantic import ValidationError

        if not isinstance(ctx.raw_dict, dict):
            return ValidationReport(
                validator="schema",
                status="BLOCK",
                reason=f"schema: top-level value is not a dict (got {type(ctx.raw_dict).__name__})",
            )

        hard_issues = [
            f"{f} is not a list (got {type(ctx.raw_dict[f]).__name__})"
            for f in _LIST_FIELDS
            if f in ctx.raw_dict and ctx.raw_dict[f] is not None
            and not isinstance(ctx.raw_dict[f], list)
        ]
        if hard_issues:
            return ValidationReport(
                validator="schema",
                status="BLOCK",
                reason=f"schema: {'; '.join(hard_issues)}",
            )

        try:
            ctx.cv_data = CvExtraction.model_validate(ctx.raw_dict)
            return ValidationReport(validator="schema", status="PASS")
        except ValidationError as exc:
            soft_issues = [f"{e['loc']}: {e['msg']}" for e in exc.errors()]

        clean = {k: v for k, v in ctx.raw_dict.items() if k in CvExtraction.model_fields}
        for f in _LIST_FIELDS:
            if f in clean and not isinstance(clean[f], list):
                clean[f] = []
        try:
            ctx.cv_data = CvExtraction.model_validate(clean)
        except ValidationError:
            ctx.cv_data = CvExtraction.model_construct(
                **{k: v for k, v in clean.items() if k in CvExtraction.model_fields}
            )

        return ValidationReport(
            validator="schema",
            status="WARN",
            reason=f"schema: {len(soft_issues)} soft violation(s): [{', '.join(soft_issues[:5])}]",
        )
