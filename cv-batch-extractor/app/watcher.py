import logging
import time
from pathlib import Path

from watchdog.events import FileCreatedEvent, FileSystemEventHandler
from watchdog.observers import Observer

from app.backend_client import notify_candidate_ready
from app.config import settings
from app.extractor import process
from app.parsers import SUPPORTED_EXTENSIONS

logger = logging.getLogger(__name__)


class CvFileHandler(FileSystemEventHandler):
    """
    Expects upload path structure: {upload_dir}/cv/{categoryId}/{documentId}/{filename}
    Only files under the cv/ subtree are processed; all other document types are ignored.
    """

    def on_created(self, event: FileCreatedEvent) -> None:
        if event.is_directory:
            return

        path = Path(event.src_path)
        if path.suffix.lower() not in SUPPORTED_EXTENSIONS:
            return

        upload_root = Path(settings.upload_dir)
        try:
            relative = path.relative_to(upload_root)
            parts = relative.parts  # (documentType, categoryId, documentId, filename)
            if len(parts) < 4:
                logger.warning("Unexpected path depth, skipping: %s", path)
                return
            if parts[0].lower() != "cv":
                return  # not a CV category — skip silently
            category_id = parts[1]
            document_id = parts[2]
        except ValueError:
            logger.warning("File outside upload dir, skipping: %s", path)
            return

        logger.info("CV detected: %s  doc=%s  cat=%s", path.name, document_id, category_id)
        try:
            json_file, _ = process(str(path), document_id, category_id)
            notify_candidate_ready(document_id, category_id, json_file)
        except Exception:
            logger.exception("Failed to process CV: %s", path)


def start() -> None:
    Path(settings.upload_dir).mkdir(parents=True, exist_ok=True)
    Path(settings.output_dir).mkdir(parents=True, exist_ok=True)

    observer = Observer()
    observer.schedule(CvFileHandler(), settings.upload_dir, recursive=True)
    observer.start()
    logger.info("Watching %s for new CV files", settings.upload_dir)

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    finally:
        observer.stop()
        observer.join()
