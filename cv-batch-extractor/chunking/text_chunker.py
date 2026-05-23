from __future__ import annotations

import logging

from chunking.base import Chunker

logger = logging.getLogger(__name__)


class TextChunker:
    """
    Splits text into overlapping fixed-size character chunks.
    Tries to break on whitespace to avoid splitting mid-word.
    """

    def __init__(self, chunk_size: int = 4_000, overlap: int = 200) -> None:
        self._size = chunk_size
        self._overlap = overlap

    def chunk(self, text: str) -> list[str]:
        if not text:
            return []

        if len(text) <= self._size:
            return [text]

        chunks: list[str] = []
        start = 0

        while start < len(text):
            end = start + self._size
            if end < len(text):
                # walk back to last whitespace to avoid mid-word splits
                ws = text.rfind(" ", start, end)
                if ws > start:
                    end = ws

            chunks.append(text[start:end].strip())
            start = end - self._overlap

        logger.debug("TextChunker produced %d chunks from %d chars", len(chunks), len(text))
        return [c for c in chunks if c]


assert isinstance(TextChunker(), Chunker)
