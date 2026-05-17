import magic

from app.guardrails.base import GuardrailReport, PipelineContext

_ALLOWED_MIME_TYPES = {
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/msword",
    "image/jpeg",
    "image/png",
    "image/tiff",
    "image/bmp",
}


class MimeTypeGuard:
    def run(self, ctx: PipelineContext) -> GuardrailReport:
        detected = magic.from_file(ctx.file_path, mime=True)

        if detected not in _ALLOWED_MIME_TYPES:
            return GuardrailReport(
                guard="mime_type",
                status="BLOCK",
                reason=f"mime_type: detected {detected!r} is not a supported document type",
                metadata={"detected_mime": detected, "allowed": sorted(_ALLOWED_MIME_TYPES)},
            )
        return GuardrailReport(
            guard="mime_type",
            status="PASS",
            metadata={"detected_mime": detected},
        )
