from __future__ import annotations

import logging
import re

from chunking.base import Chunker

logger = logging.getLogger(__name__)

_SECTION_RE = re.compile(
    r"(?m)^(?:EXPERIENCE|EDUCATION|SKILLS|PROJECTS|CERTIFICATIONS|"
    r"PUBLICATIONS|LANGUAGES|SUMMARY|OBJECTIVE|PROFILE|WORK|EMPLOYMENT)"
    r"\s*$",
    re.IGNORECASE,
)


class SemanticChunker:
    """
    Splits CV text at natural section boundaries (EXPERIENCE, EDUCATION, etc.)
    before falling back to fixed-size splitting for oversized sections.

    TODO: Replace regex heuristics with an embedding-based boundary detector
          for documents that don't use standard section headings.
    """

    def __init__(self, max_chunk_size: int = 4_000) -> None:
        self._max = max_chunk_size

    def chunk(self, text: str) -> list[str]:
        if not text:
            return []

        sections = _SECTION_RE.split(text)
        chunks: list[str] = []

        for section in sections:
            section = section.strip()
            if not section:
                continue
            if len(section) > self._max:
                # fall back to fixed-size for oversized sections
                from chunking.text_chunker import TextChunker
                chunks.extend(TextChunker(self._max).chunk(section))
            else:
                chunks.append(section)

        logger.debug("SemanticChunker produced %d chunks", len(chunks))
        return chunks or [text]


assert isinstance(SemanticChunker(), Chunker)
