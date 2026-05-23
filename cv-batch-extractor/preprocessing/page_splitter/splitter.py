from __future__ import annotations

import logging
from pathlib import Path

logger = logging.getLogger(__name__)


class PageSplitter:
    """
    Splits a multi-page PDF into individual page images for per-page OCR routing.

    TODO: Install dependency — `pip install pymupdf pillow`
    TODO: Add config knob for output DPI (default 300).
    TODO: Return list[str] paths when per-page OCR is needed (currently no-op).
    """

    def split(self, file_path: str, dpi: int = 300) -> list[str]:
        """
        Returns a list of image paths, one per page.
        If splitting fails, returns [file_path] (single-item fallback).
        """
        try:
            return self._split(file_path, dpi)
        except Exception as exc:
            logger.warning("Page split failed, treating as single file (%s): %s", file_path, exc)
            return [file_path]

    @staticmethod
    def _split(file_path: str, dpi: int) -> list[str]:
        import fitz
        import io
        from PIL import Image

        doc = fitz.open(file_path)
        out_dir = Path(file_path).parent / (Path(file_path).stem + "_pages")
        out_dir.mkdir(exist_ok=True)

        paths: list[str] = []
        for i, page in enumerate(doc):
            pix = page.get_pixmap(dpi=dpi)
            img = Image.open(io.BytesIO(pix.tobytes("png")))
            out_path = out_dir / f"page_{i + 1:04d}.png"
            img.save(str(out_path))
            paths.append(str(out_path))

        return paths
