from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, runtime_checkable


@dataclass
class OCRResult:
    text: str
    pages: int = 1
    confidence: float | None = None
    engine: str = "unknown"


@runtime_checkable
class OCRAdapter(Protocol):
    def extract(self, file_path: str) -> OCRResult: ...
    def is_available(self) -> bool: ...
