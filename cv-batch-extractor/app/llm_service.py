import logging
import time
from pathlib import Path

import requests

from app.config import settings

logger = logging.getLogger(__name__)

_PROMPT_TEMPLATE = (Path(__file__).parent.parent / "cv_prompt.txt").read_text()


def ask_llm(text: str) -> str:
    prompt = _PROMPT_TEMPLATE.replace("{{TEXT}}", text)
    last_error: Exception | None = None

    for attempt in range(1, settings.llm_max_retries + 1):
        try:
            resp = requests.post(
                f"{settings.ollama_url}/api/generate",
                json={"model": settings.ollama_model, "prompt": prompt, "stream": False},
                timeout=settings.llm_timeout,
            )
            resp.raise_for_status()
            return resp.json()["response"]
        except Exception as exc:
            last_error = exc
            logger.warning("LLM attempt %d/%d failed: %s", attempt, settings.llm_max_retries, exc)
            if attempt < settings.llm_max_retries:
                time.sleep(settings.llm_retry_delay * attempt)

    raise RuntimeError(f"LLM failed after {settings.llm_max_retries} attempts") from last_error
