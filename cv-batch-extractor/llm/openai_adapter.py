from __future__ import annotations

import logging

from llm.base import LLMAdapter

logger = logging.getLogger(__name__)


class OpenAIAdapter:
    """
    LLM adapter for the OpenAI API (chat completions).

    Requires: pip install openai
    Set env vars: OPENAI_API_KEY, OPENAI_MODEL (default gpt-4o)

    TODO: Add token-usage logging and cost tracking.
    TODO: Support streaming responses for large documents.
    TODO: Wire retry + circuit breaker (reuse pattern from OllamaAdapter).
    """

    def __init__(self) -> None:
        self._client = None

    def _get_client(self):
        if self._client is None:
            try:
                from openai import OpenAI
                from config.settings import settings
                self._client = OpenAI(api_key=settings.openai_api_key)
            except ImportError as exc:
                raise RuntimeError(
                    "openai package not installed. Run: pip install openai"
                ) from exc
        return self._client

    def complete(self, prompt: str) -> str:
        from config.settings import settings
        client = self._get_client()
        response = client.chat.completions.create(
            model=settings.openai_model,
            messages=[{"role": "user", "content": prompt}],
            timeout=settings.llm_timeout,
        )
        result = response.choices[0].message.content or ""
        logger.info("OpenAI responded with %d chars", len(result))
        return result

    def is_available(self) -> bool:
        try:
            from openai import OpenAI  # noqa: F401
            from config.settings import settings
            return bool(settings.openai_api_key)
        except ImportError:
            return False


assert isinstance(OpenAIAdapter(), LLMAdapter)
