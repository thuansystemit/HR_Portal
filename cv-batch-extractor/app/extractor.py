import json
import logging
import re
from pathlib import Path

from app.config import settings
from app.llm_service import ask_llm
from app.parsers import extract_text

logger = logging.getLogger(__name__)


def _slugify(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.lower()).strip("_")


def _build_output_filename(full_name: str | None, document_id: str) -> str:
    name = (full_name or "unknown").strip()
    parts = name.split()
    first = _slugify(parts[0]) if parts else "unknown"
    last = _slugify(parts[-1]) if len(parts) >= 2 else ""
    short_id = document_id.replace("-", "")[:8]
    slug = f"{first}_{last}" if last else first
    return f"cv_{slug}_{short_id}.json"


def _extract_json(raw: str) -> dict:
    """
    Tolerant JSON extractor — handles markdown fences, leading/trailing prose,
    and any other noise the LLM wraps around the JSON object.
    """
    text = raw.strip()

    # Strip markdown code fences
    text = re.sub(r"(?s)^```(?:json)?\s*", "", text)
    text = re.sub(r"\s*```\s*$", "", text)
    text = text.strip()

    # Fast path — already clean JSON
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    # Extract the outermost {...} block to skip any leading/trailing prose
    start = text.find("{")
    end = text.rfind("}")
    if start != -1 and end != -1 and end > start:
        try:
            return json.loads(text[start : end + 1])
        except json.JSONDecodeError:
            pass

    logger.error("LLM response could not be parsed as JSON. Raw output:\n%s", raw)
    raise ValueError("LLM did not return valid JSON")


def process(file_path: str, document_id: str, category_id: str) -> tuple[str, dict]:
    logger.info("Extracting text from %s (doc=%s)", file_path, document_id)
    text = extract_text(file_path)

    if not text or not text.strip():
        raise ValueError(f"No text extracted from {file_path}")

    logger.info("Calling LLM for document %s", document_id)
    raw = ask_llm(text)

    data = _extract_json(raw)

    filename = _build_output_filename(data.get("fullName"), document_id)
    output_path = Path(settings.output_dir) / filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    logger.info("Saved extraction result to %s", output_path)
    return filename, data
