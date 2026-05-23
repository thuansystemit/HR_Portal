from __future__ import annotations

from typing import Protocol, runtime_checkable


@runtime_checkable
class LLMAdapter(Protocol):
    def complete(self, prompt: str) -> str: ...
    def is_available(self) -> bool: ...
