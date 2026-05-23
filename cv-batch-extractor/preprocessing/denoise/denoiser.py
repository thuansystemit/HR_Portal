from __future__ import annotations

import logging

logger = logging.getLogger(__name__)


class Denoiser:
    """
    Removes noise from scanned images to improve OCR accuracy.

    TODO: Install dependency — `pip install opencv-python`
    TODO: Add config knob for filter strength (h parameter in fastNlMeansDenoising).
    TODO: Benchmark against bilateral filter for text preservation.
    """

    def denoise(self, file_path: str) -> str:
        """Returns path to the denoised image."""
        try:
            return self._denoise(file_path)
        except Exception as exc:
            logger.warning("Denoise failed, using original (%s): %s", file_path, exc)
            return file_path

    @staticmethod
    def _denoise(file_path: str) -> str:
        import cv2
        from pathlib import Path

        img = cv2.imread(file_path, cv2.IMREAD_GRAYSCALE)
        if img is None:
            return file_path

        denoised = cv2.fastNlMeansDenoising(img, h=10, templateWindowSize=7, searchWindowSize=21)

        out = Path(file_path).with_stem(Path(file_path).stem + "_denoised")
        cv2.imwrite(str(out), denoised)
        return str(out)
