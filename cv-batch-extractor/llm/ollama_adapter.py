from __future__ import annotations

import logging
import threading
import time
from enum import Enum

import requests

from config.settings import settings
from llm.base import LLMAdapter

logger = logging.getLogger(__name__)


class CircuitOpenError(RuntimeError):
    pass


class _State(Enum):
    CLOSED = "CLOSED"
    OPEN = "OPEN"
    HALF_OPEN = "HALF_OPEN"


class _CircuitBreaker:
    def __init__(self) -> None:
        self._state = _State.CLOSED
        self._failure_times: list[float] = []
        self._opened_at: float | None = None
        self._lock = threading.Lock()

    def check(self) -> None:
        with self._lock:
            if self._state == _State.OPEN:
                elapsed = time.monotonic() - (self._opened_at or 0)
                if elapsed >= settings.cb_cooldown_seconds:
                    self._state = _State.HALF_OPEN
                    logger.info("Circuit breaker entering HALF_OPEN — probing Ollama")
                else:
                    raise CircuitOpenError(
                        f"Circuit OPEN — Ollama suspended for "
                        f"{settings.cb_cooldown_seconds - elapsed:.0f}s more"
                    )

    def record_success(self) -> None:
        with self._lock:
            if self._state != _State.CLOSED:
                logger.info("Circuit breaker CLOSED — Ollama recovered")
            self._failure_times.clear()
            self._state = _State.CLOSED
            self._opened_at = None

    def record_failure(self) -> None:
        now = time.monotonic()
        with self._lock:
            self._failure_times = [
                t for t in self._failure_times
                if now - t < settings.cb_window_seconds
            ]
            self._failure_times.append(now)
            if len(self._failure_times) >= settings.cb_failure_threshold:
                if self._state != _State.OPEN:
                    logger.warning(
                        "Circuit breaker OPENING after %d failures in %ds",
                        len(self._failure_times),
                        settings.cb_window_seconds,
                    )
                self._state = _State.OPEN
                self._opened_at = now

    @property
    def state(self) -> str:
        return self._state.value


class OllamaAdapter:
    """
    LLM adapter for a locally-hosted Ollama instance.
    Wraps the /api/generate endpoint with retry logic and a circuit breaker.
    """

    def __init__(self) -> None:
        self._breaker = _CircuitBreaker()

    def complete(self, prompt: str) -> str:
        last_error: Exception | None = None

        for attempt in range(1, settings.llm_max_retries + 1):
            self._breaker.check()
            try:
                resp = requests.post(
                    f"{settings.ollama_url}/api/generate",
                    json={
                        "model": settings.ollama_model,
                        "prompt": prompt,
                        "stream": False,
                    },
                    timeout=settings.llm_timeout,
                )
                resp.raise_for_status()
                result = resp.json()["response"]
                self._breaker.record_success()
                return result
            except CircuitOpenError:
                raise
            except Exception as exc:
                last_error = exc
                self._breaker.record_failure()
                logger.warning(
                    "Ollama attempt %d/%d failed: %s",
                    attempt,
                    settings.llm_max_retries,
                    exc,
                )
                if attempt < settings.llm_max_retries:
                    time.sleep(settings.llm_retry_delay * attempt)

        raise RuntimeError(
            f"Ollama failed after {settings.llm_max_retries} attempts"
        ) from last_error

    def is_available(self) -> bool:
        try:
            resp = requests.get(
                f"{settings.ollama_url}/api/tags", timeout=5
            )
            return resp.status_code == 200
        except Exception:
            return False


assert isinstance(OllamaAdapter(), LLMAdapter)
