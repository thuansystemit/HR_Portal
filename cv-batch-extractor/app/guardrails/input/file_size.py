import os

from app.config import settings
from app.guardrails.base import GuardrailReport, PipelineContext


class FileSizeGuard:
    def run(self, ctx: PipelineContext) -> GuardrailReport:
        size_bytes = os.path.getsize(ctx.file_path)
        size_mb = size_bytes / (1024 * 1024)
        limit_mb = settings.max_file_size_mb

        if size_mb > limit_mb:
            return GuardrailReport(
                guard="file_size",
                status="BLOCK",
                reason=f"file_size: {size_mb:.1f} MB exceeds limit of {limit_mb} MB",
                metadata={"size_mb": round(size_mb, 2), "limit_mb": limit_mb},
            )
        return GuardrailReport(guard="file_size", status="PASS")
