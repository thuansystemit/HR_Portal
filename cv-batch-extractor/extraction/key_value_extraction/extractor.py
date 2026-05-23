from __future__ import annotations

import logging

logger = logging.getLogger(__name__)


class KeyValueExtractor:
    """
    Extracts key-value pairs from semi-structured forms and CVs.

    TODO: Implement regex-based heuristics for common CV fields
          (Name:, Email:, Phone:, Date of Birth:, etc.).
    TODO: Integrate a form-understanding model (e.g. LayoutLM, Donut) for
          complex form layouts.
    TODO: Return dict[str, str] of detected key-value pairs pre-LLM extraction
          to seed the prompt with high-confidence fields.
    """

    def extract(self, text: str) -> dict[str, str]:
        """Returns key-value pairs found in the text."""
        logger.debug("Key-value extraction (stub) — %d chars", len(text))
        return {}
