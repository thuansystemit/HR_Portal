import logging
import threading
from concurrent.futures import ThreadPoolExecutor

from app.backend_client import notify_candidate_ready, notify_invoice_ready, notify_extraction_failed
from app.config import settings
from app.dead_letter import append as dead_letter_append
from app.pipeline import Pipeline, ProcessingResult

logger = logging.getLogger(__name__)

_pipeline = Pipeline()


def _process_task(document_id: str, category_id: str, file_path: str, document_type: str) -> None:
    result: ProcessingResult = _pipeline.run(document_id, category_id, file_path, document_type)

    if result.output_file is not None:
        try:
            if result.document_type == "INVOICE":
                notify_invoice_ready(
                    document_id=result.document_id,
                    category_id=result.category_id,
                    json_file=result.output_file,
                )
            else:
                notify_candidate_ready(
                    document_id=result.document_id,
                    category_id=result.category_id,
                    json_file=result.output_file,
                )
        except Exception as exc:
            logger.exception(
                "Failed to notify backend for document %s (status=%s)",
                document_id,
                result.status,
            )
            notify_extraction_failed(document_id, "INGEST", str(exc))
    else:
        logger.warning(
            "Document %s status=%s — skipping backend notification (no output file)",
            document_id,
            result.status,
        )
        reason = result.error or next(
            (r.reason for r in result.reports if r.status == "BLOCK"),
            "pipeline returned no output",
        )
        notify_extraction_failed(document_id, "PIPELINE", reason or "unknown")

    if result.status in ("REJECTED", "ERROR"):
        reason = result.error or next(
            (r.reason for r in result.reports if r.status == "BLOCK"),
            "unknown",
        )
        dead_letter_append(
            document_id=document_id,
            category_id=category_id,
            file_path=file_path,
            status=result.status,
            reason=reason or "unknown",
        )


class WorkerPool:
    def __init__(self) -> None:
        # Semaphore bounds total in-flight + queued tasks across the executor
        capacity = settings.worker_max_workers + settings.worker_queue_size
        self._semaphore = threading.BoundedSemaphore(capacity)
        self._executor = ThreadPoolExecutor(max_workers=settings.worker_max_workers)

    def submit(self, document_id: str, category_id: str, file_path: str, document_type: str = "CV") -> None:
        if not self._semaphore.acquire(blocking=False):
            logger.error(
                "Worker queue full — dead-lettering document %s immediately", document_id
            )
            dead_letter_append(
                document_id=document_id,
                category_id=category_id,
                file_path=file_path,
                status="REJECTED",
                reason="worker_pool: queue full, document could not be scheduled",
            )
            return

        self._executor.submit(self._run, document_id, category_id, file_path, document_type)

    def _run(self, document_id: str, category_id: str, file_path: str, document_type: str) -> None:
        try:
            _process_task(document_id, category_id, file_path, document_type)
        finally:
            self._semaphore.release()

    def shutdown(self) -> None:
        logger.info("Shutting down worker pool — waiting for in-flight tasks to complete")
        self._executor.shutdown(wait=True)
        logger.info("Worker pool shut down")
