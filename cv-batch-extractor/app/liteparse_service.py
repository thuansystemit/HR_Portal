import json
import logging
import subprocess
from pathlib import Path

from app.parsers import extract_text

logger = logging.getLogger(__name__)

_LIT_TIMEOUT = 120


def _call_liteparse_cli(file_path: str) -> str:
    """Call the LiteParse CLI and return concatenated text from all pages."""
    result = subprocess.run(
        ["lit", "parse", file_path, "--output", "json"],
        capture_output=True,
        text=True,
        timeout=_LIT_TIMEOUT,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"liteparse exited {result.returncode}: {result.stderr.strip()}"
        )

    data = json.loads(result.stdout)

    # LiteParse output: list of pages, each with a list of text blocks
    pages = data if isinstance(data, list) else data.get("pages", [])
    parts: list[str] = []
    for page in pages:
        blocks = page.get("blocks") or page.get("text_blocks") or []
        for block in blocks:
            text = block.get("text") or block.get("content") or ""
            if text.strip():
                parts.append(text.strip())

    # Fallback: if structured blocks not found, try a top-level text key
    if not parts:
        top_text = data.get("text") or data.get("content") or ""
        if top_text:
            parts.append(top_text)

    return "\n\n".join(parts)


def extract_text_liteparse(file_path: str) -> str:
    """
    Extract text using the LiteParse CLI.
    Falls back to the existing parsers.extract_text() if the CLI is unavailable or fails.
    """
    try:
        text = _call_liteparse_cli(file_path)
        if text.strip():
            logger.info("LiteParse extracted %d chars from %s", len(text), Path(file_path).name)
            return text
        logger.warning("LiteParse returned empty text for %s — falling back", file_path)
    except FileNotFoundError:
        logger.warning("'lit' command not found — falling back to built-in parser")
    except Exception as exc:
        logger.warning("LiteParse failed for %s (%s) — falling back", file_path, exc)

    return extract_text(file_path)
