from __future__ import annotations

import json
import logging
import subprocess
from pathlib import Path

from ocr.base import OCRAdapter, OCRResult

logger = logging.getLogger(__name__)

_TIMEOUT = 120


class LiteParserAdapter:
    """
    OCR adapter that uses the LiteParse CLI (`lit parse`).
    Falls back to built-in parsers (PyMuPDF / python-docx / pytesseract) when
    the CLI is unavailable or returns empty output.
    """

    def extract(self, file_path: str) -> OCRResult:
        try:
            text = self._liteparse(file_path)
            if text.strip():
                logger.info(
                    "LiteParser extracted %d chars from %s",
                    len(text),
                    Path(file_path).name,
                )
                return OCRResult(text=text, engine="liteparser")
            logger.warning("LiteParser returned empty text — falling back: %s", file_path)
        except FileNotFoundError:
            logger.warning("'lit' CLI not found — falling back to built-in parsers")
        except Exception as exc:
            logger.warning("LiteParser failed (%s) — falling back: %s", exc, file_path)

        text = self._builtin(file_path)
        return OCRResult(text=text, engine="builtin")

    def is_available(self) -> bool:
        try:
            result = subprocess.run(
                ["lit", "--version"], capture_output=True, text=True, timeout=5
            )
            return result.returncode == 0
        except Exception:
            return False

    # ── private ────────────────────────────────────────────────────────────

    @staticmethod
    def _liteparse(file_path: str) -> str:
        result = subprocess.run(
            ["lit", "parse", file_path, "--output", "json"],
            capture_output=True,
            text=True,
            timeout=_TIMEOUT,
        )
        if result.returncode != 0:
            raise RuntimeError(
                f"liteparse exited {result.returncode}: {result.stderr.strip()}"
            )

        data = json.loads(result.stdout)
        pages = data if isinstance(data, list) else data.get("pages", [])
        parts: list[str] = []

        for page in pages:
            blocks = page.get("blocks") or page.get("text_blocks") or []
            for block in blocks:
                text = block.get("text") or block.get("content") or ""
                if text.strip():
                    parts.append(text.strip())

        if not parts:
            top = data.get("text") or data.get("content") or ""
            if top:
                parts.append(top)

        return "\n\n".join(parts)

    @staticmethod
    def _builtin(file_path: str) -> str:
        lower = file_path.lower()
        if lower.endswith(".pdf"):
            import fitz
            doc = fitz.open(file_path)
            return "\n".join(page.get_text() for page in doc)
        if lower.endswith((".docx", ".doc")):
            from docx import Document
            doc = Document(file_path)
            return "\n".join(p.text for p in doc.paragraphs)
        from PIL import Image
        import pytesseract
        img = Image.open(file_path)
        return pytesseract.image_to_string(img)


assert isinstance(LiteParserAdapter(), OCRAdapter)
