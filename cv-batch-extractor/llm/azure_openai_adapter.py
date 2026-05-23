from __future__ import annotations

import logging

from llm.base import LLMAdapter

logger = logging.getLogger(__name__)


class AzureOpenAIAdapter:
    """
    LLM adapter for Azure OpenAI Service.

    Requires: pip install openai
    Set env vars:
        AZURE_OPENAI_ENDPOINT, AZURE_OPENAI_API_KEY,
        AZURE_OPENAI_DEPLOYMENT, AZURE_OPENAI_API_VERSION

    TODO: Add token-usage logging.
    TODO: Wire retry + circuit breaker.
    TODO: Support managed identity auth (DefaultAzureCredential).
    """

    def __init__(self) -> None:
        self._client = None

    def _get_client(self):
        if self._client is None:
            try:
                from openai import AzureOpenAI
                from config.settings import settings
                self._client = AzureOpenAI(
                    azure_endpoint=settings.azure_openai_endpoint,
                    api_key=settings.azure_openai_api_key,
                    api_version=settings.azure_openai_api_version,
                )
            except ImportError as exc:
                raise RuntimeError(
                    "openai package not installed. Run: pip install openai"
                ) from exc
        return self._client

    def complete(self, prompt: str) -> str:
        from config.settings import settings
        client = self._get_client()
        response = client.chat.completions.create(
            model=settings.azure_openai_deployment,
            messages=[{"role": "user", "content": prompt}],
            timeout=settings.llm_timeout,
        )
        result = response.choices[0].message.content or ""
        logger.info("Azure OpenAI responded with %d chars", len(result))
        return result

    def is_available(self) -> bool:
        try:
            from openai import AzureOpenAI  # noqa: F401
            from config.settings import settings
            return bool(settings.azure_openai_endpoint and settings.azure_openai_api_key)
        except ImportError:
            return False


assert isinstance(AzureOpenAIAdapter(), LLMAdapter)
