from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    upload_dir: str = "/app/uploads"
    output_dir: str = "/app/output"
    backend_url: str = "http://backend:8080"
    backend_timeout: int = 30
    internal_api_key: str = ""

    ollama_url: str = "http://host.docker.internal:11434"
    ollama_model: str = "llama3"
    llm_timeout: int = 300
    llm_max_retries: int = 3
    llm_retry_delay: float = 2.0

    # Input guard thresholds
    max_file_size_mb: float = 20.0
    max_text_chars: int = 40_000
    min_text_chars: int = 50
    min_word_count: int = 30
    min_printable_ratio: float = 0.85

    # Worker pool
    worker_max_workers: int = 4
    worker_queue_size: int = 100

    # Circuit breaker
    cb_failure_threshold: int = 5
    cb_window_seconds: int = 60
    cb_cooldown_seconds: int = 30

    # Output sanitize field length caps (chars)
    sanitize_max_full_name: int = 200
    sanitize_max_email: int = 320
    sanitize_max_summary: int = 5_000
    sanitize_max_list_item: int = 1_000
    sanitize_max_default: int = 500

    class Config:
        env_file = ".env"


settings = Settings()
