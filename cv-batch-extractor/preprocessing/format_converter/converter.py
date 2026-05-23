from __future__ import annotations

import logging
import subprocess
from pathlib import Path

logger = logging.getLogger(__name__)


class FormatConverter:
    """
    Converts document formats to a canonical form for downstream processing.
    Primary use: DOC/DOCX → PDF so the PDF OCR path handles everything uniformly.

    System requirement: LibreOffice (`apt-get install libreoffice`)

    TODO: Add PPTX → PDF conversion.
    TODO: Support Windows via COM automation as an alternative to LibreOffice.
    """

    def to_pdf(self, file_path: str) -> str:
        """
        Converts *file_path* to PDF using LibreOffice headless.
        Returns the path to the generated PDF.
        Raises RuntimeError if conversion fails.
        """
        path = Path(file_path)
        out_dir = path.parent
        out_pdf = out_dir / (path.stem + ".pdf")

        if out_pdf.exists():
            return str(out_pdf)

        try:
            result = subprocess.run(
                [
                    "libreoffice",
                    "--headless",
                    "--convert-to", "pdf",
                    "--outdir", str(out_dir),
                    str(path),
                ],
                capture_output=True,
                text=True,
                timeout=120,
            )
            if result.returncode != 0:
                raise RuntimeError(
                    f"LibreOffice exited {result.returncode}: {result.stderr.strip()}"
                )
            logger.info("Converted %s → %s", path.name, out_pdf.name)
            return str(out_pdf)

        except FileNotFoundError as exc:
            raise RuntimeError(
                "LibreOffice not found. Install with: apt-get install libreoffice"
            ) from exc
