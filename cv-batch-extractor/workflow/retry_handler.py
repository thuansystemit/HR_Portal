from __future__ import annotations

import logging
import time
from collections.abc import Callable
from typing import TypeVar

from config.settings import settings

logger = logging.getLogger(__name__)

T = TypeVar("T")


class RetryHandler:
    """
    Executes a callable with exponential back-off retry.
    Designed for transient network / IO failures — not for logic errors.
    """

    def __init__(
        self,
        max_retries: int | None = None,
        delay: float | None = None,
        exceptions: tuple[type[Exception], ...] = (Exception,),
    ) -> None:
        self._max_retries = max_retries if max_retries is not None else settings.llm_max_retries
        self._delay = delay if delay is not None else settings.llm_retry_delay
        self._exceptions = exceptions

    def run(self, fn: Callable[[], T], label: str = "operation") -> T:
        last_exc: Exception | None = None

        for attempt in range(1, self._max_retries + 1):
            try:
                return fn()
            except self._exceptions as exc:
                last_exc = exc
                logger.warning(
                    "%s attempt %d/%d failed: %s",
                    label,
                    attempt,
                    self._max_retries,
                    exc,
                )
                if attempt < self._max_retries:
                    time.sleep(self._delay * attempt)

        raise RuntimeError(
            f"{label} failed after {self._max_retries} attempt(s)"
        ) from last_exc
