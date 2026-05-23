from __future__ import annotations

import json
import logging
import threading
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

import requests

from config.settings import settings
from domain.models import ProcessingResult
from workflow.extraction_pipeline import ExtractionPipeline

logger = logging.getLogger(__name__)

_HEADERS: dict[str, str] = (
    {"X-Internal-Api-Key": settings.internal_api_key}
    if settings.internal_api_key
    else {}
)

_DEAD_LETTER_FILE = Path(settings.output_dir) / "dead_letter.ndjson"

_pipeline = ExtractionPipeline()


# ── backend notification (GAP-001 + GAP-002 fix) ──────────────────────────────

def _notify_backend(
    document_id: str,
    category_id: str,
    json_file: str | None,
    extraction_status: str,
    guardrail_warnings: list[str],
) -> None:
    """Always notifies the backend regardless of extraction outcome."""
    url = f"{settings.backend_url}/api/v1/cv-candidates"
    payload = {
        "documentId": document_id,
        "documentCategoryId": category_id,
        "jsonFile": json_file,                       # null on REJECTED / ERROR
        "extractionStatus": extraction_status,       # GAP-002: new field
        "guardrailWarnings": guardrail_warnings,     # GAP-002: new field
    }
    try:
        logger.info(
            "POST %s  status=%s  jsonFile=%s", url, extraction_status, json_file
        )
        resp = requests.post(
            url, json=payload, headers=_HEADERS, timeout=settings.backend_timeout
        )
        resp.raise_for_status()
        logger.info("Backend ack %s for document %s", resp.status_code, document_id)
    except Exception:
        logger.exception("Failed to notify backend for document %s", document_id)


def _dead_letter(
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
        logger.warning("Dead-lettered document %s  status=%s  reason=%s", document_id, status, reason)
    except Exception:
        logger.exception("Failed to write dead-letter for document %s", document_id)


def _process_task(document_id: str, category_id: str, file_path: str) -> None:
    result: ProcessingResult = _pipeline.run(document_id, category_id, file_path)

    # GAP-001 fix: always notify backend, even on REJECTED / ERROR
    _notify_backend(
        document_id=result.document_id,
        category_id=result.category_id,
        json_file=result.output_file,            # None when REJECTED / ERROR
        extraction_status=result.status,
        guardrail_warnings=result.warnings,
    )

    if result.status in ("REJECTED", "ERROR"):
        reason = result.error or next(
            (r.reason for r in result.reports if r.status == "BLOCK"), "unknown"
        )
        _dead_letter(
            document_id=document_id,
            category_id=category_id,
            file_path=file_path,
            status=result.status,
            reason=reason or "unknown",
        )


# ── worker pool ────────────────────────────────────────────────────────────────

class WorkerPool:
    def __init__(self) -> None:
        capacity = settings.worker_max_workers + settings.worker_queue_size
        self._semaphore = threading.BoundedSemaphore(capacity)
        self._executor = ThreadPoolExecutor(max_workers=settings.worker_max_workers)

    def submit(self, document_id: str, category_id: str, file_path: str) -> None:
        if not self._semaphore.acquire(blocking=False):
            logger.error("Queue full — dead-lettering document %s", document_id)
            reason = "worker_pool: queue full, document could not be scheduled"

            _dead_letter(
                document_id=document_id,
                category_id=category_id,
                file_path=file_path,
                status="REJECTED",
                reason=reason,
            )

            # GAP-007 fix: notify backend even on QUEUE_FULL
            _notify_backend(
                document_id=document_id,
                category_id=category_id,
                json_file=None,
                extraction_status="REJECTED",
                guardrail_warnings=[reason],
            )
            return

        self._executor.submit(self._run, document_id, category_id, file_path)

    def _run(self, document_id: str, category_id: str, file_path: str) -> None:
        try:
            _process_task(document_id, category_id, file_path)
        finally:
            self._semaphore.release()

    def shutdown(self) -> None:
        logger.info("Shutting down worker pool — waiting for in-flight tasks")
        self._executor.shutdown(wait=True)
        logger.info("Worker pool shut down")
