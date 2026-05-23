from __future__ import annotations

import logging

from chunking.base import Chunker

logger = logging.getLogger(__name__)

_PAGE_BREAK = "\x0c"


class PageChunker:
    """
    Splits text on form-feed characters (\\f / \\x0c) inserted by PDF parsers
    to mark page boundaries. Merges small pages to avoid excessive chunks.
    """

    def __init__(self, min_page_chars: int = 100) -> None:
        self._min = min_page_chars

    def chunk(self, text: str) -> list[str]:
        if not text:
            return []

        pages = text.split(_PAGE_BREAK)
        merged: list[str] = []
        buffer = ""

        for page in pages:
            page = page.strip()
            if not page:
                continue
            buffer = (buffer + "\n\n" + page).strip() if buffer else page
            if len(buffer) >= self._min:
                merged.append(buffer)
                buffer = ""

        if buffer:
            if merged:
                merged[-1] += "\n\n" + buffer
            else:
                merged.append(buffer)

        logger.debug("PageChunker produced %d chunks from %d pages", len(merged), len(pages))
        return merged


assert isinstance(PageChunker(), Chunker)
