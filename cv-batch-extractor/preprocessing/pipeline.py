from __future__ import annotations

import logging
from pathlib import Path

from preprocessing.format_converter.converter import FormatConverter
from preprocessing.image_enhancement.enhancer import ImageEnhancer
from preprocessing.deskew.deskewer import Deskewer
from preprocessing.denoise.denoiser import Denoiser
from preprocessing.page_splitter.splitter import PageSplitter

logger = logging.getLogger(__name__)


class PreprocessingPipeline:
    """
    Runs the appropriate preprocessing steps based on document type.

    image documents  → enhance → deskew → denoise
    pdf documents    → page_split (if multi-page for OCR routing)
    doc/docx         → convert to PDF, then page_split
    """

    def __init__(self) -> None:
        self._enhancer = ImageEnhancer()
        self._deskewer = Deskewer()
        self._denoiser = Denoiser()
        self._splitter = PageSplitter()
        self._converter = FormatConverter()

    def process(self, file_path: str, document_type: str) -> str:
        """
        Returns the path to the preprocessed file (may be the same as input
        if no transformation was needed).
        """
        path = Path(file_path)
        logger.debug("Preprocessing %s (type=%s)", path.name, document_type)

        if document_type in ("doc", "docx"):
            file_path = self._converter.to_pdf(file_path)
            document_type = "pdf"

        if document_type == "image":
            file_path = self._enhancer.enhance(file_path)
            file_path = self._deskewer.deskew(file_path)
            file_path = self._denoiser.denoise(file_path)

        return file_path
