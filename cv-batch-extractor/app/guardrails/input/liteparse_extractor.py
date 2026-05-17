from app.guardrails.base import GuardrailReport, PipelineContext
from app.liteparse_service import extract_text_liteparse


class LiteParseTextExtractor:
    def run(self, ctx: PipelineContext) -> GuardrailReport:
        try:
            ctx.raw_text = extract_text_liteparse(ctx.file_path)
        except Exception as exc:
            return GuardrailReport(
                guard="liteparse_extractor",
                status="BLOCK",
                reason=f"liteparse_extractor: failed to extract text — {exc}",
            )
        return GuardrailReport(guard="liteparse_extractor", status="PASS")
