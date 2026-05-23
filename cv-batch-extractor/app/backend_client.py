import logging

import requests

from app.config import settings

logger = logging.getLogger(__name__)

_HEADERS = (
    {"X-Internal-Api-Key": settings.internal_api_key}
    if settings.internal_api_key
    else {}
)


def get_extraction_mode(category_id: str) -> bool:
    """Return True if the category has LLM extraction enabled (default True on error)."""
    url = f"{settings.backend_url}/api/v1/categories/{category_id}"
    try:
        resp = requests.get(url, headers=_HEADERS, timeout=settings.backend_timeout)
        resp.raise_for_status()
        return resp.json().get("llmExtraction", True)
    except Exception as exc:
        logger.warning(
            "Could not fetch extraction mode for category %s — defaulting to LLM: %s",
            category_id, exc,
        )
        return True


def notify_extraction_failed(document_id: str, error_phase: str, error_message: str) -> None:
    url = f"{settings.backend_url}/api/v1/documents/{document_id}/extraction-status"
    payload = {
        "status": "FAILED",
        "errorPhase": error_phase,
        "errorMessage": error_message[:500],
    }
    try:
        resp = requests.patch(url, json=payload, headers=_HEADERS, timeout=settings.backend_timeout)
        resp.raise_for_status()
        logger.info("Document %s marked FAILED (phase=%s)", document_id, error_phase)
    except Exception as exc:
        logger.warning("Could not mark document %s as FAILED: %s", document_id, exc)


def notify_candidate_ready(
    document_id: str,
    category_id: str,
    json_file: str,
) -> None:
    url = f"{settings.backend_url}/api/v1/cv-candidates"
    payload = {
        "documentId": document_id,
        "documentCategoryId": category_id,
        "jsonFile": json_file,
    }
    logger.info("POST %s  jsonFile=%s", url, json_file)
    resp = requests.post(url, json=payload, headers=_HEADERS, timeout=settings.backend_timeout)
    resp.raise_for_status()
    logger.info("Backend responded %s for document %s", resp.status_code, document_id)


def notify_invoice_ready(
    document_id: str,
    category_id: str,
    json_file: str,
) -> None:
    url = f"{settings.backend_url}/api/v1/invoice-records"
    payload = {
        "documentId": document_id,
        "documentCategoryId": category_id,
        "jsonFile": json_file,
    }
    logger.info("POST %s  jsonFile=%s", url, json_file)
    resp = requests.post(url, json=payload, headers=_HEADERS, timeout=settings.backend_timeout)
    resp.raise_for_status()
    logger.info("Backend responded %s for document %s", resp.status_code, document_id)
