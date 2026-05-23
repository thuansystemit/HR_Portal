from __future__ import annotations

from typing import Literal

from llm.base import LLMAdapter


def create(
    provider: Literal["ollama", "openai", "azure_openai", "anthropic"] = "ollama",
) -> LLMAdapter:
    if provider == "ollama":
        from llm.ollama_adapter import OllamaAdapter
        return OllamaAdapter()

    if provider == "openai":
        from llm.openai_adapter import OpenAIAdapter
        return OpenAIAdapter()

    if provider == "azure_openai":
        from llm.azure_openai_adapter import AzureOpenAIAdapter
        return AzureOpenAIAdapter()

    if provider == "anthropic":
        from llm.anthropic_adapter import AnthropicAdapter
        return AnthropicAdapter()

    raise ValueError(
        f"Unknown LLM provider: {provider!r}. "
        "Choose ollama | openai | azure_openai | anthropic"
    )
