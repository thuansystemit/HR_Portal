from __future__ import annotations

import logging
from pathlib import Path

logger = logging.getLogger(__name__)

_MIME_TO_TYPE: dict[str, str] = {
    "application/pdf": "pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "docx",
    "application/msword": "doc",
    "image/jpeg": "image",
    "image/png": "image",
    "image/tiff": "image",
    "image/bmp": "image",
}

_EXT_TO_TYPE: dict[str, str] = {
    ".pdf": "pdf",
    ".docx": "docx",
    ".doc": "doc",
    ".jpg": "image",
    ".jpeg": "image",
    ".png": "image",
    ".tiff": "image",
    ".tif": "image",
    ".bmp": "image",
}


class DocumentClassifier:
    """
    Determines document type from magic bytes (preferred) or file extension (fallback).
    Returns one of: 'pdf', 'docx', 'doc', 'image', 'unknown'.
    """

    def classify(self, file_path: str) -> str:
        mime = self._detect_mime(file_path)
        if mime and mime in _MIME_TO_TYPE:
            doc_type = _MIME_TO_TYPE[mime]
            logger.debug("Classified %s as %s (mime: %s)", file_path, doc_type, mime)
            return doc_type

        ext = Path(file_path).suffix.lower()
        doc_type = _EXT_TO_TYPE.get(ext, "unknown")
        logger.debug("Classified %s as %s (extension fallback)", file_path, doc_type)
        return doc_type

    @staticmethod
    def _detect_mime(file_path: str) -> str | None:
        try:
            import magic
            return magic.from_file(file_path, mime=True)
        except ImportError:
            return None
        except Exception as exc:
            logger.warning("MIME detection failed for %s: %s", file_path, exc)
            return None
