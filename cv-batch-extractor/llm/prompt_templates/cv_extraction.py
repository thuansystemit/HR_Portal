from __future__ import annotations

from pathlib import Path

_TEMPLATE_FILE = Path(__file__).parent.parent.parent / "cv_prompt.txt"

_TEMPLATE: str | None = None


def _load() -> str:
    global _TEMPLATE
    if _TEMPLATE is None:
        _TEMPLATE = _TEMPLATE_FILE.read_text(encoding="utf-8")
    return _TEMPLATE


def build(text: str) -> str:
    """Return the full extraction prompt with *text* substituted in."""
    return _load().replace("{{TEXT}}", text)
