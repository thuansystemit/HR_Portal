from __future__ import annotations

import logging

from ocr.base import OCRAdapter, OCRResult

logger = logging.getLogger(__name__)


class PaddleOCRAdapter:
    """
    OCR adapter backed by PaddleOCR.

    TODO: Install dependency — `pip install paddleocr paddlepaddle`
    TODO: Support multi-language models via config (lang= parameter).
    TODO: Return per-block confidence scores aggregated into OCRResult.confidence.
    """

    def __init__(self) -> None:
        self._ocr = None

    def _get_engine(self):
        if self._ocr is None:
            try:
                from paddleocr import PaddleOCR
                self._ocr = PaddleOCR(use_angle_cls=True, lang="en", show_log=False)
            except ImportError as exc:
                raise RuntimeError(
                    "paddleocr is not installed. Run: pip install paddleocr paddlepaddle"
                ) from exc
        return self._ocr

    def extract(self, file_path: str) -> OCRResult:
        engine = self._get_engine()
        result = engine.ocr(file_path, cls=True)

        lines: list[str] = []
        confidences: list[float] = []

        for page in (result or []):
            for line in (page or []):
                if line and len(line) >= 2:
                    text, conf = line[1][0], line[1][1]
                    if text.strip():
                        lines.append(text.strip())
                        confidences.append(float(conf))

        text = "\n".join(lines)
        avg_conf = sum(confidences) / len(confidences) if confidences else None

        logger.info("PaddleOCR extracted %d chars from %s", len(text), file_path)
        return OCRResult(
            text=text,
            confidence=avg_conf,
            engine="paddleocr",
        )

    def is_available(self) -> bool:
        try:
            import paddleocr  # noqa: F401
            return True
        except ImportError:
            return False


assert isinstance(PaddleOCRAdapter(), OCRAdapter)
