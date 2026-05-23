from __future__ import annotations

import logging
import shutil
import uuid
from pathlib import Path

from config.settings import settings

logger = logging.getLogger(__name__)


class UploadService:
    """
    Receives a file from an HTTP upload or external source and places it in the
    canonical upload directory structure so the file watcher picks it up.

    Target path: {upload_dir}/cv/{category_id}/{document_id}/{original_filename}

    TODO: Wire to a FastAPI or Flask endpoint if a direct HTTP upload path is needed
          in addition to the filesystem-watch ingestion path.
    """

    def accept(
        self,
        source_path: str,
        category_id: str,
        document_id: str | None = None,
        original_filename: str | None = None,
    ) -> tuple[str, str]:
        """
        Copy *source_path* into the upload directory.

        Returns (document_id, dest_path).
        """
        doc_id = document_id or str(uuid.uuid4())
        filename = original_filename or Path(source_path).name

        dest_dir = Path(settings.upload_dir) / "cv" / category_id / doc_id
        dest_dir.mkdir(parents=True, exist_ok=True)

        dest_path = dest_dir / filename
        shutil.copy2(source_path, dest_path)

        logger.info(
            "Accepted upload: doc=%s  cat=%s  file=%s", doc_id, category_id, dest_path
        )
        return doc_id, str(dest_path)
