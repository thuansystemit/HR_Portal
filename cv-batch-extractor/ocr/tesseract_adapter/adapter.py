from __future__ import annotations

import logging
from pathlib import Path

from ocr.base import OCRAdapter, OCRResult

logger = logging.getLogger(__name__)


class TesseractAdapter:
    """
    OCR adapter backed by Tesseract via pytesseract.

    System requirement: `apt-get install tesseract-ocr`
    Python requirement: `pip install pytesseract pillow pymupdf`

    TODO: Support multi-page PDFs by rendering each page via PyMuPDF before OCR.
    TODO: Expose lang= config knob for non-English documents.
    TODO: Return word-level confidence via image_to_data().
    """

    def extract(self, file_path: str) -> OCRResult:
        try:
            import pytesseract
            from PIL import Image
        except ImportError as exc:
            raise RuntimeError(
                "pytesseract / pillow not installed. Run: pip install pytesseract pillow"
            ) from exc

        path = Path(file_path)
        pages_text: list[str] = []

        if path.suffix.lower() == ".pdf":
            pages_text = self._extract_pdf(path)
        else:
            img = Image.open(file_path)
            pages_text = [pytesseract.image_to_string(img)]

        text = "\n\n".join(t for t in pages_text if t.strip())
        logger.info("Tesseract extracted %d chars from %s", len(text), path.name)
        return OCRResult(text=text, pages=len(pages_text), engine="tesseract")

    def is_available(self) -> bool:
        try:
            import pytesseract
            pytesseract.get_tesseract_version()
            return True
        except Exception:
            return False

    @staticmethod
    def _extract_pdf(path: Path) -> list[str]:
        try:
            import fitz
            import pytesseract
            from PIL import Image
            import io

            doc = fitz.open(str(path))
            pages: list[str] = []
            for page in doc:
                pix = page.get_pixmap(dpi=300)
                img = Image.open(io.BytesIO(pix.tobytes("png")))
                pages.append(pytesseract.image_to_string(img))
            return pages
        except Exception as exc:
            logger.warning("PDF→Tesseract rendering failed: %s", exc)
            return []


assert isinstance(TesseractAdapter(), OCRAdapter)
