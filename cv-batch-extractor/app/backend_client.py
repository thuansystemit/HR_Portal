import logging

import requests

from app.config import settings

logger = logging.getLogger(__name__)


def notify_candidate_ready(document_id: str, category_id: str, json_file: str) -> None:
    url = f"{settings.backend_url}/api/v1/cv-candidates"
    payload = {
        "documentId": document_id,
        "documentCategoryId": category_id,
        "jsonFile": json_file,
    }
    headers = {"X-Internal-Api-Key": settings.internal_api_key} if settings.internal_api_key else {}
    logger.info("POST %s  jsonFile=%s", url, json_file)
    resp = requests.post(url, json=payload, headers=headers, timeout=settings.backend_timeout)
    resp.raise_for_status()
    logger.info("Backend responded %s for document %s", resp.status_code, document_id)
