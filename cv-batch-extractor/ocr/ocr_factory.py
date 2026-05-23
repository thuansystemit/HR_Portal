from __future__ import annotations

from typing import Literal

from ocr.base import OCRAdapter


def create(
    engine: Literal["liteparser", "paddleocr", "tesseract"] = "liteparser",
) -> OCRAdapter:
    if engine == "liteparser":
        from ocr.liteparser_adapter.adapter import LiteParserAdapter
        return LiteParserAdapter()

    if engine == "paddleocr":
        from ocr.paddleocr_adapter.adapter import PaddleOCRAdapter
        return PaddleOCRAdapter()

    if engine == "tesseract":
        from ocr.tesseract_adapter.adapter import TesseractAdapter
        return TesseractAdapter()

    raise ValueError(f"Unknown OCR engine: {engine!r}. Choose liteparser | paddleocr | tesseract")
