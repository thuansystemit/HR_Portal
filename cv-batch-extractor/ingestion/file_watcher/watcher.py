from __future__ import annotations

import logging
import time
from pathlib import Path

from watchdog.events import FileCreatedEvent, FileSystemEventHandler
from watchdog.observers import Observer

from config.settings import settings

logger = logging.getLogger(__name__)

_SUPPORTED_EXTENSIONS = {".pdf", ".doc", ".docx", ".jpg", ".jpeg", ".png", ".tiff", ".tif", ".bmp"}

_ROUTED_TYPES = {"cv", "technical"}


class CvFileHandler(FileSystemEventHandler):
    """
    Watches the upload directory for new documents.

    Expected path structure:
        {upload_dir}/{documentType}/{categoryId}/{documentId}/{filename}

    Supported document types: cv, technical.
    """

    def __init__(self, submit_fn) -> None:
        self._submit = submit_fn

    def on_created(self, event: FileCreatedEvent) -> None:
        if event.is_directory:
            return

        path = Path(event.src_path)
        if path.suffix.lower() not in _SUPPORTED_EXTENSIONS:
            return

        try:
            relative = path.relative_to(Path(settings.upload_dir))
            parts = relative.parts  # (documentType, categoryId, documentId, filename)
        except ValueError:
            logger.warning("File outside upload dir, skipping: %s", path)
            return

        if len(parts) < 4:
            logger.warning("Unexpected path depth (%d parts), skipping: %s", len(parts), path)
            return

        doc_type = parts[0].lower()
        if doc_type not in _ROUTED_TYPES:
            return

        category_id = parts[1]
        document_id = parts[2]

        logger.info(
            "Document detected: type=%s  file=%s  doc=%s  cat=%s",
            doc_type.upper(), path.name, document_id, category_id,
        )
        self._submit(document_id, category_id, str(path), doc_type.upper())


def start(submit_fn) -> None:
    """
    Start the filesystem watcher.  Blocks until KeyboardInterrupt.

    submit_fn(document_id, category_id, file_path) → None
    """
    Path(settings.upload_dir).mkdir(parents=True, exist_ok=True)
    Path(settings.output_dir).mkdir(parents=True, exist_ok=True)

    observer = Observer()
    observer.schedule(CvFileHandler(submit_fn), settings.upload_dir, recursive=True)
    observer.start()
    logger.info("Watching %s for new documents (cv, technical)", settings.upload_dir)

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    finally:
        observer.stop()
        observer.join()
