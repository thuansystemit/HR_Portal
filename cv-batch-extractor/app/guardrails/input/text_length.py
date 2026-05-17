from app.config import settings
from app.guardrails.base import GuardrailReport, PipelineContext


class TextLengthGuard:
    def run(self, ctx: PipelineContext) -> GuardrailReport:
        text = (ctx.raw_text or "").strip()
        length = len(text)

        if length < settings.min_text_chars:
            return GuardrailReport(
                guard="text_length",
                status="BLOCK",
                reason=(
                    f"text_length: {length} characters extracted — "
                    "document may be empty or image-only"
                ),
                metadata={"chars": length, "min": settings.min_text_chars},
            )

        if len(ctx.raw_text) > settings.max_text_chars:
            original_len = len(ctx.raw_text)
            ctx.raw_text = ctx.raw_text[: settings.max_text_chars]
            return GuardrailReport(
                guard="text_length",
                status="WARN",
                reason=(
                    f"text_length: truncated from {original_len:,} "
                    f"to {settings.max_text_chars:,} characters"
                ),
                metadata={"original_chars": original_len, "max": settings.max_text_chars},
            )

        return GuardrailReport(
            guard="text_length",
            status="PASS",
            metadata={"chars": length},
        )
