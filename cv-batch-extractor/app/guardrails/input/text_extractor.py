from app.guardrails.base import GuardrailReport, PipelineContext
from app.parsers import extract_text


class TextExtractor:
    def run(self, ctx: PipelineContext) -> GuardrailReport:
        try:
            ctx.raw_text = extract_text(ctx.file_path)
        except Exception as exc:
            return GuardrailReport(
                guard="text_extractor",
                status="BLOCK",
                reason=f"text_extractor: failed to extract text — {exc}",
            )
        return GuardrailReport(guard="text_extractor", status="PASS")
