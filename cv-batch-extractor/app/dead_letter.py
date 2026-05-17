import json
import logging
from datetime import datetime, timezone
from pathlib import Path

from app.config import settings

logger = logging.getLogger(__name__)

_DEAD_LETTER_FILE = Path(settings.output_dir) / "dead_letter.ndjson"


def append(
    document_id: str,
    category_id: str,
    file_path: str,
    status: str,
    reason: str,
) -> None:
    entry = {
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S"),
        "documentId": document_id,
        "categoryId": category_id,
        "filePath": file_path,
        "status": status,
        "reason": reason,
    }
    try:
        _DEAD_LETTER_FILE.parent.mkdir(parents=True, exist_ok=True)
        with _DEAD_LETTER_FILE.open("a", encoding="utf-8") as f:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
        logger.warning(
            "Dead-lettered document %s  status=%s  reason=%s",
            document_id,
            status,
            reason,
        )
    except Exception:
        logger.exception(
            "Failed to write dead-letter entry for document %s", document_id
        )
