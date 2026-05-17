import logging
import re

from app.guardrails.base import GuardrailReport, PipelineContext

logger = logging.getLogger(__name__)

_PATTERNS: list[re.Pattern] = [
    re.compile(r"(?i)ignore\s+(all\s+)?previous\s+instructions"),
    re.compile(r"(?i)disregard\s+(the\s+)?(above|previous|prior)"),
    re.compile(r"(?i)you\s+are\s+now\s+a"),
    re.compile(r"(?i)act\s+as\s+a\s+(?!recruiter|hiring)"),
    re.compile(r"(?i)system\s*prompt"),
    re.compile(r"(?i)jailbreak"),
    re.compile(r"(?i)<\s*/?(?:system|instructions?|prompt)\s*>"),
]


class InjectionGuard:
    def run(self, ctx: PipelineContext) -> GuardrailReport:
        text = ctx.raw_text or ""
        matches_found = 0

        sanitized = text
        for pattern in _PATTERNS:
            new_text, count = pattern.subn("[REDACTED]", sanitized)
            if count:
                matches_found += count
                sanitized = new_text

        ctx.prompt_text = sanitized

        if matches_found:
            logger.warning(
                "Prompt injection patterns detected in document %s (%d match(es))",
                ctx.document_id,
                matches_found,
            )
            return GuardrailReport(
                guard="injection",
                status="WARN",
                reason=(
                    f"injection: {matches_found} potential prompt-override "
                    "pattern(s) detected and redacted"
                ),
                metadata={"match_count": matches_found},
            )

        return GuardrailReport(guard="injection", status="PASS")
