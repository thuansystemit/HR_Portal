from __future__ import annotations

from typing import Literal

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # ── paths ──────────────────────────────────────────────────────────────
    upload_dir: str = "/app/uploads"
    output_dir: str = "/app/output"

    # ── backend ────────────────────────────────────────────────────────────
    backend_url: str = "http://backend:8080"
    backend_timeout: int = 30
    internal_api_key: str = ""

    # ── OCR engine ─────────────────────────────────────────────────────────
    ocr_engine: Literal["liteparser", "paddleocr", "tesseract"] = "liteparser"
    liteparse_timeout: int = 60

    # ── LLM provider ───────────────────────────────────────────────────────
    llm_provider: Literal["ollama", "openai", "azure_openai", "anthropic"] = "ollama"
    llm_timeout: int = 300
    llm_max_retries: int = 3
    llm_retry_delay: float = 2.0

    # Ollama
    ollama_url: str = "http://host.docker.internal:11434"
    ollama_model: str = "llama3"

    # OpenAI
    openai_api_key: str = ""
    openai_model: str = "gpt-4o"

    # Azure OpenAI
    azure_openai_endpoint: str = ""
    azure_openai_api_key: str = ""
    azure_openai_deployment: str = ""
    azure_openai_api_version: str = "2024-02-01"

    # Anthropic
    anthropic_api_key: str = ""
    anthropic_model: str = "claude-sonnet-4-6"

    # ── input validation thresholds ────────────────────────────────────────
    max_file_size_mb: float = 20.0
    max_text_chars: int = 40_000
    min_text_chars: int = 50
    min_word_count: int = 30
    min_printable_ratio: float = 0.85
    allowed_mime_types: list[str] = [
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "image/jpeg",
        "image/png",
        "image/tiff",
        "image/bmp",
    ]
    injection_patterns: list[str] = [
        r"(?i)ignore\s+(all\s+)?previous\s+instructions",
        r"(?i)disregard\s+(the\s+)?(above|previous|prior)",
        r"(?i)you\s+are\s+now\s+a",
        r"(?i)act\s+as\s+a\s+(?!recruiter|hiring)",
        r"(?i)system\s*prompt",
        r"(?i)jailbreak",
        r"(?i)<\s*/?(?:system|instructions?|prompt)\s*>",
    ]

    # ── worker pool ────────────────────────────────────────────────────────
    worker_max_workers: int = 4
    worker_queue_size: int = 100

    # ── circuit breaker ────────────────────────────────────────────────────
    cb_failure_threshold: int = 5
    cb_window_seconds: int = 60
    cb_cooldown_seconds: int = 30

    # ── chunking ───────────────────────────────────────────────────────────
    chunk_size: int = 4_000
    chunk_overlap: int = 200

    # ── output sanitize field caps (chars) ────────────────────────────────
    sanitize_max_full_name: int = 200
    sanitize_max_email: int = 320
    sanitize_max_summary: int = 5_000
    sanitize_max_list_item: int = 1_000
    sanitize_max_default: int = 500

    model_config = {"env_file": ".env"}


settings = Settings()
