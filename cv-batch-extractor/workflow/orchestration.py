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

def _notify_cv_backend(
    document_id: str,
    category_id: str,
    json_file: str | None,
    extraction_status: str,
    guardrail_warnings: list[str],
) -> None:
    """Notifies the CV candidates endpoint."""
    url = f"{settings.backend_url}/api/v1/cv-candidates"
    payload = {
        "documentId": document_id,
        "documentCategoryId": category_id,
        "jsonFile": json_file,
        "extractionStatus": extraction_status,
        "guardrailWarnings": guardrail_warnings,
    }
    try:
        logger.info("POST %s  status=%s  jsonFile=%s", url, extraction_status, json_file)
        resp = requests.post(url, json=payload, headers=_HEADERS, timeout=settings.backend_timeout)
        resp.raise_for_status()
        logger.info("Backend ack %s for document %s", resp.status_code, document_id)
    except Exception:
        logger.exception("Failed to notify CV backend for document %s", document_id)


# keep old name as alias so any callers that haven't been updated still work
_notify_backend = _notify_cv_backend


def _notify_knowledge_backend(
    document_id: str,
    category_id: str,
    extraction_status: str,
    guardrail_warnings: list[str],
    knowledge_data,
) -> None:
    """Notifies the knowledge ingest endpoint for TECHNICAL documents."""
    from domain.technical_schema import KnowledgeExtraction
    url = f"{settings.backend_url}/api/v1/knowledge/ingest"

    kd: KnowledgeExtraction | None = knowledge_data
    payload: dict = {
        "documentId": document_id,
        "categoryId": category_id,
        "extractionStatus": extraction_status,
        "guardrailWarnings": guardrail_warnings,
        "documentType": "TECHNICAL",
        "title": kd.title if kd else None,
        "summary": kd.summary if kd else None,
        "technologies": [t.model_dump() for t in kd.technologies] if kd else [],
        "concepts": [c.model_dump() for c in kd.concepts] if kd else [],
        "relationships": [r.model_dump() for r in kd.relationships] if kd else [],
    }
    try:
        logger.info("POST %s  status=%s  entities=%d",
                    url, extraction_status,
                    len(payload["technologies"]) + len(payload["concepts"]))
        resp = requests.post(url, json=payload, headers=_HEADERS, timeout=settings.backend_timeout)
        resp.raise_for_status()
        logger.info("Knowledge backend ack %s for document %s", resp.status_code, document_id)
    except Exception:
        logger.exception("Failed to notify knowledge backend for document %s", document_id)


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


def _process_task(document_id: str, category_id: str, file_path: str, document_type: str = "CV") -> None:
    result: ProcessingResult = _pipeline.run(document_id, category_id, file_path, document_type)

    if document_type == "TECHNICAL":
        _notify_knowledge_backend(
            document_id=result.document_id,
            category_id=result.category_id,
            extraction_status=result.status,
            guardrail_warnings=result.warnings,
            knowledge_data=result.knowledge_data,
        )
    else:
        _notify_cv_backend(
            document_id=result.document_id,
            category_id=result.category_id,
            json_file=result.output_file,
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

    def submit(self, document_id: str, category_id: str, file_path: str, document_type: str = "CV") -> None:
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

            if document_type == "TECHNICAL":
                _notify_knowledge_backend(
                    document_id=document_id,
                    category_id=category_id,
                    extraction_status="REJECTED",
                    guardrail_warnings=[reason],
                    knowledge_data=None,
                )
            else:
                _notify_cv_backend(
                    document_id=document_id,
                    category_id=category_id,
                    json_file=None,
                    extraction_status="REJECTED",
                    guardrail_warnings=[reason],
                )
            return

        self._executor.submit(self._run, document_id, category_id, file_path, document_type)

    def _run(self, document_id: str, category_id: str, file_path: str, document_type: str = "CV") -> None:
        try:
            _process_task(document_id, category_id, file_path, document_type)
        finally:
            self._semaphore.release()

    def shutdown(self) -> None:
        logger.info("Shutting down worker pool — waiting for in-flight tasks")
        self._executor.shutdown(wait=True)
        logger.info("Worker pool shut down")
