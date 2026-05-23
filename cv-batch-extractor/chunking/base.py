from __future__ import annotations

from typing import Protocol, runtime_checkable


@runtime_checkable
class Chunker(Protocol):
    def chunk(self, text: str) -> list[str]: ...
