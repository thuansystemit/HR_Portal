from __future__ import annotations

import logging

logger = logging.getLogger(__name__)


class ImageEnhancer:
    """
    Improves image contrast and brightness before OCR.

    TODO: Install dependency — `pip install opencv-python pillow`
    TODO: Implement adaptive histogram equalization (CLAHE) for low-contrast scans.
    TODO: Auto-detect and correct colour-space (grayscale vs RGB).
    """

    def enhance(self, file_path: str) -> str:
        """Returns path to the enhanced image (writes a new file alongside original)."""
        try:
            return self._enhance(file_path)
        except Exception as exc:
            logger.warning("Image enhancement failed, using original (%s): %s", file_path, exc)
            return file_path

    @staticmethod
    def _enhance(file_path: str) -> str:
        from PIL import Image, ImageEnhance
        from pathlib import Path

        img = Image.open(file_path).convert("L")  # grayscale
        img = ImageEnhance.Contrast(img).enhance(1.5)
        img = ImageEnhance.Sharpness(img).enhance(2.0)

        out = Path(file_path).with_stem(Path(file_path).stem + "_enhanced")
        img.save(str(out))
        return str(out)
