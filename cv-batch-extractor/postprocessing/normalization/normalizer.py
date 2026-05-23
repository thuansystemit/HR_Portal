from __future__ import annotations

import logging
import re

from config.settings import settings
from domain.cv_schema import CvExtraction
from domain.models import PipelineContext, ValidationReport

logger = logging.getLogger(__name__)

_CTRL_RE = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")
_MULTI_NEWLINE_RE = re.compile(r"\n{3,}")

_FIELD_CAPS: dict[str, int] = {}


def _caps() -> dict[str, int]:
    global _FIELD_CAPS
    if not _FIELD_CAPS:
        _FIELD_CAPS = {
            "fullName": settings.sanitize_max_full_name,
            "email": settings.sanitize_max_email,
            "summary": settings.sanitize_max_summary,
        }
    return _FIELD_CAPS


def _clean(value: str, cap: int) -> tuple[str, bool]:
    """Returns (cleaned_value, was_truncated)."""
    value = _CTRL_RE.sub("", value)
    value = _MULTI_NEWLINE_RE.sub("\n\n", value)
    value = value.strip()
    if len(value) > cap:
        return value[:cap], True
    return value, False


def _clean_list(items: list[str], cap: int) -> tuple[list[str], list[int]]:
    result, truncated_indices = [], []
    for i, s in enumerate(items):
        if not isinstance(s, str):
            continue
        cleaned, was_truncated = _clean(s, cap)
        result.append(cleaned)
        if was_truncated:
            truncated_indices.append(i)
    return result, truncated_indices


class Normalizer:
    """
    GAP-005 fix — post-LLM output sanitizer with:
    • field-specific length caps read from config
    • control-character stripping
    • 3+ newline collapse → 2
    • truncated field names appended to cv_data.lowConfidenceFields
    """

    def normalize(self, ctx: PipelineContext) -> ValidationReport:
        if ctx.cv_data is None:
            return ValidationReport(validator="normalize", status="PASS")

        cv = ctx.cv_data
        caps = _caps()
        default_cap = settings.sanitize_max_default
        list_item_cap = settings.sanitize_max_list_item
        truncated: list[str] = []

        # ── scalar string fields ───────────────────────────────────────────
        for field_name in (
            "fullName", "email", "phone", "city", "country",
            "linkedinUrl", "githubUrl", "portfolioUrl", "summary", "rawLanguage"
        ):
            val = getattr(cv, field_name, None)
            if isinstance(val, str):
                cap = caps.get(field_name, default_cap)
                cleaned, trunc = _clean(val, cap)
                setattr(cv, field_name, cleaned)
                if trunc:
                    truncated.append(field_name)

        # ── flat list-of-string fields ─────────────────────────────────────
        for attr in ("toolsAndFrameworks", "softSkills", "technicalSkills",
                     "lowConfidenceFields", "missingFields"):
            cleaned_list, _ = _clean_list(getattr(cv, attr), default_cap)
            setattr(cv, attr, cleaned_list)

        # ── work experiences ───────────────────────────────────────────────
        for i, exp in enumerate(cv.workExperiences):
            for f in ("company", "title", "location"):
                v = getattr(exp, f, None)
                if isinstance(v, str):
                    cleaned, trunc = _clean(v, default_cap)
                    setattr(exp, f, cleaned)
                    if trunc:
                        truncated.append(f"workExperiences[{i}].{f}")
            for lst_attr in ("responsibilities", "achievements", "technologies"):
                cleaned_list, idxs = _clean_list(getattr(exp, lst_attr), list_item_cap)
                setattr(exp, lst_attr, cleaned_list)
                for idx in idxs:
                    truncated.append(f"workExperiences[{i}].{lst_attr}[{idx}]")

        # ── educations ─────────────────────────────────────────────────────
        for i, edu in enumerate(cv.educations):
            for f in ("institution", "degree", "fieldOfStudy", "honors"):
                v = getattr(edu, f, None)
                if isinstance(v, str):
                    cleaned, trunc = _clean(v, default_cap)
                    setattr(edu, f, cleaned)
                    if trunc:
                        truncated.append(f"educations[{i}].{f}")

        # ── projects ───────────────────────────────────────────────────────
        for i, proj in enumerate(cv.projects):
            for f in ("name", "description", "url"):
                v = getattr(proj, f, None)
                if isinstance(v, str):
                    cap = settings.sanitize_max_summary if f == "description" else default_cap
                    cleaned, trunc = _clean(v, cap)
                    setattr(proj, f, cleaned)
                    if trunc:
                        truncated.append(f"projects[{i}].{f}")
            proj.technologies, _ = _clean_list(proj.technologies, default_cap)

        # ── publications ───────────────────────────────────────────────────
        for i, pub in enumerate(cv.publications):
            for f in ("title", "journal", "url"):
                v = getattr(pub, f, None)
                if isinstance(v, str):
                    cleaned, trunc = _clean(v, default_cap)
                    setattr(pub, f, cleaned)
                    if trunc:
                        truncated.append(f"publications[{i}].{f}")

        # ── certifications ─────────────────────────────────────────────────
        for i, cert in enumerate(cv.certifications):
            for f in ("name", "issuer", "credentialId"):
                v = getattr(cert, f, None)
                if isinstance(v, str):
                    cleaned, trunc = _clean(v, default_cap)
                    setattr(cert, f, cleaned)
                    if trunc:
                        truncated.append(f"certifications[{i}].{f}")

        # ── append truncated field paths to lowConfidenceFields ────────────
        if truncated:
            existing = set(cv.lowConfidenceFields)
            cv.lowConfidenceFields = list(existing | set(truncated))
            logger.debug("Normalizer truncated %d field(s): %s", len(truncated), truncated[:10])

        return ValidationReport(validator="normalize", status="PASS")
