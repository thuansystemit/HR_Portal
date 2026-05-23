from __future__ import annotations

import logging
from pathlib import Path

logger = logging.getLogger(__name__)


class MetadataExtractor:
    """
    Extracts document-level metadata (author, creation date, page count, file size).

    TODO: Use PyMuPDF metadata for PDFs (doc.metadata).
    TODO: Use python-docx core_properties for DOCX files.
    TODO: Surface metadata in PipelineContext.doc_metadata for audit / search indexing.
    """

    def extract(self, file_path: str) -> dict:
        path = Path(file_path)
        meta: dict = {
            "filename": path.name,
            "extension": path.suffix.lower(),
            "size_bytes": path.stat().st_size if path.exists() else None,
        }

        try:
            meta.update(self._pdf_meta(file_path))
        except Exception:
            pass

        logger.debug("Metadata for %s: %s", path.name, meta)
        return meta

    @staticmethod
    def _pdf_meta(file_path: str) -> dict:
        import fitz
        doc = fitz.open(file_path)
        m = doc.metadata or {}
        return {
            "pages": doc.page_count,
            "author": m.get("author"),
            "title": m.get("title"),
            "creator": m.get("creator"),
            "created": m.get("creationDate"),
        }
