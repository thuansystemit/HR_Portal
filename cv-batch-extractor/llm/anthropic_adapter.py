from __future__ import annotations

import logging

from llm.base import LLMAdapter

logger = logging.getLogger(__name__)


class AnthropicAdapter:
    """
    LLM adapter for Anthropic Claude models.

    Requires: pip install anthropic
    Set env vars: ANTHROPIC_API_KEY, ANTHROPIC_MODEL (default claude-sonnet-4-6)

    TODO: Enable prompt caching (cache_control on the system prompt block)
          to reduce cost on repeated CV extractions with the same prompt template.
    TODO: Wire retry + circuit breaker.
    TODO: Add token-usage logging.
    """

    def __init__(self) -> None:
        self._client = None

    def _get_client(self):
        if self._client is None:
            try:
                import anthropic
                from config.settings import settings
                self._client = anthropic.Anthropic(api_key=settings.anthropic_api_key)
            except ImportError as exc:
                raise RuntimeError(
                    "anthropic package not installed. Run: pip install anthropic"
                ) from exc
        return self._client

    def complete(self, prompt: str) -> str:
        from config.settings import settings
        client = self._get_client()
        message = client.messages.create(
            model=settings.anthropic_model,
            max_tokens=4096,
            messages=[{"role": "user", "content": prompt}],
        )
        result = message.content[0].text if message.content else ""
        logger.info("Anthropic responded with %d chars", len(result))
        return result

    def is_available(self) -> bool:
        try:
            import anthropic  # noqa: F401
            from config.settings import settings
            return bool(settings.anthropic_api_key)
        except ImportError:
            return False


assert isinstance(AnthropicAdapter(), LLMAdapter)
