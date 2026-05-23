import logging

from app.chunking import chunk_document
from app.guardrails.base import GuardrailReport, PipelineContext

logger = logging.getLogger(__name__)


class DocumentChunkerGuard:
    """
    Splits the extracted text into semantic chunks and stores them in ctx.chunks.
    Always PASSes — chunking is best-effort and never blocks processing.
    The chunks are consumed by the embedding pipeline (Phase 3).
    """

    def run(self, ctx: PipelineContext) -> GuardrailReport:
        text = ctx.prompt_text or ctx.raw_text or ""
        if not text.strip():
            return GuardrailReport(guard="chunker", status="PASS",
                                   metadata={"chunk_count": 0})

        chunks = chunk_document(text, ctx.document_type)
        ctx.chunks = chunks

        logger.debug(
            "Document %s chunked into %d section(s): %s",
            ctx.document_id,
            len(chunks),
            [c.section for c in chunks],
        )

        return GuardrailReport(
            guard="chunker",
            status="PASS",
            metadata={
                "chunk_count": len(chunks),
                "sections": [c.section for c in chunks],
            },
        )
