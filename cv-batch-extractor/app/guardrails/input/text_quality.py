from app.config import settings
from app.guardrails.base import GuardrailReport, PipelineContext


class TextQualityGuard:
    def run(self, ctx: PipelineContext) -> GuardrailReport:
        text = ctx.raw_text or ""
        issues: list[str] = []
        metadata: dict = {}

        word_count = len(text.split())
        metadata["word_count"] = word_count
        if word_count < settings.min_word_count:
            issues.append(
                f"low word count ({word_count} words, min {settings.min_word_count})"
            )

        if text:
            printable_ratio = sum(c.isprintable() for c in text) / len(text)
            metadata["printable_ratio"] = round(printable_ratio, 3)
            if printable_ratio < settings.min_printable_ratio:
                issues.append(
                    f"low printable character ratio "
                    f"({printable_ratio:.2f}, min {settings.min_printable_ratio}) "
                    "— possible garbled OCR"
                )

        if issues:
            return GuardrailReport(
                guard="text_quality",
                status="WARN",
                reason="text_quality: " + "; ".join(issues),
                metadata=metadata,
            )
        return GuardrailReport(guard="text_quality", status="PASS", metadata=metadata)
