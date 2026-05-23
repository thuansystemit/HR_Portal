from __future__ import annotations

import logging

logger = logging.getLogger(__name__)


class Deskewer:
    """
    Detects and corrects page skew in scanned images.

    TODO: Install dependency — `pip install opencv-python numpy`
    TODO: Use Hough line transform to detect dominant angle.
    TODO: Fall back gracefully when skew angle < 0.5° (no-op).
    """

    def deskew(self, file_path: str) -> str:
        """Returns path to the deskewed image."""
        try:
            return self._deskew(file_path)
        except Exception as exc:
            logger.warning("Deskew failed, using original (%s): %s", file_path, exc)
            return file_path

    @staticmethod
    def _deskew(file_path: str) -> str:
        import cv2
        import numpy as np
        from pathlib import Path

        img = cv2.imread(file_path, cv2.IMREAD_GRAYSCALE)
        if img is None:
            return file_path

        _, binary = cv2.threshold(img, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
        coords = np.column_stack(np.where(binary > 0))
        angle = cv2.minAreaRect(coords)[-1]

        if angle < -45:
            angle = -(90 + angle)
        else:
            angle = -angle

        if abs(angle) < 0.5:
            return file_path

        h, w = img.shape
        center = (w // 2, h // 2)
        M = cv2.getRotationMatrix2D(center, angle, 1.0)
        rotated = cv2.warpAffine(img, M, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)

        out = Path(file_path).with_stem(Path(file_path).stem + "_deskewed")
        cv2.imwrite(str(out), rotated)
        return str(out)
