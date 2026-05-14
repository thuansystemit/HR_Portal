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

    class Config:
        env_file = ".env"


settings = Settings()
