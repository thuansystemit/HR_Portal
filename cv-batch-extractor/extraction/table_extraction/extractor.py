from __future__ import annotations

import logging

logger = logging.getLogger(__name__)


class TableExtractor:
    """
    Extracts structured tables from documents.

    TODO: Integrate camelot-py (PDF) or img2table (images) for table parsing.
    TODO: Return list[dict] where each dict has 'headers' and 'rows' keys.
    TODO: Handle merged cells and multi-row headers.
    """

    def extract(self, file_path: str) -> list[dict]:
        """Returns a list of extracted tables as dicts with headers + rows."""
        logger.debug("Table extraction (stub) for %s", file_path)
        return []
