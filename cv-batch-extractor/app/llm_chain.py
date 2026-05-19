import logging
import threading
import time
from enum import Enum
from pathlib import Path

from langchain_core.exceptions import OutputParserException
from langchain_core.messages import HumanMessage
from langchain_core.output_parsers import PydanticOutputParser
from langchain_ollama import ChatOllama

from app.config import settings
from app.domain.cv_schema import CvExtraction

logger = logging.getLogger(__name__)

__all__ = ["invoke_extraction", "CircuitOpenError", "OutputParserException"]

# ── Prompt ─────────────────────────────────────────────────────────────────────
# Use plain str.replace so the JSON schema's { } characters in cv_prompt.txt
# are never misread as f-string template variables.

_PROMPT_TEMPLATE = (Path(__file__).parent.parent / "cv_prompt.txt").read_text()


def _build_messages(text: str) -> list[HumanMessage]:
    return [HumanMessage(content=_PROMPT_TEMPLATE.replace("{{TEXT}}", text))]


# ── Circuit Breaker ─────────────────────────────────────────────────────────────


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
                    logger.info("Circuit breaker entering HALF_OPEN — probing LLM")
                else:
                    raise CircuitOpenError(
                        f"Circuit breaker OPEN — LLM suspended for "
                        f"{settings.cb_cooldown_seconds - elapsed:.0f}s more"
                    )

    def record_success(self) -> None:
        with self._lock:
            if self._state != _State.CLOSED:
                logger.info("Circuit breaker CLOSED — LLM recovered")
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
                        "Circuit breaker OPENING after %d failures in %ds window",
                        len(self._failure_times),
                        settings.cb_window_seconds,
                    )
                self._state = _State.OPEN
                self._opened_at = now

    @property
    def state(self) -> str:
        return self._state.value


_breaker = _CircuitBreaker()

# ── LLM & Parser ───────────────────────────────────────────────────────────────
# format="json" instructs Ollama to constrain token generation to valid JSON,
# eliminating the need for fence-stripping and raw json.loads() calls.

_llm = ChatOllama(
    base_url=settings.ollama_url,
    model=settings.ollama_model,
    temperature=0.0,
    format="json",
    timeout=float(settings.llm_timeout),
)

_parser = PydanticOutputParser(pydantic_object=CvExtraction)

# ── Self-correction ─────────────────────────────────────────────────────────────


def _self_correct(failed_content: str, error: str) -> str:
    """Ask the LLM to fix its own structurally invalid JSON output."""
    fix_prompt = (
        "The JSON you returned does not match the required CV extraction schema.\n"
        f"Error: {error}\n\n"
        "Return ONLY the corrected JSON. No explanation, no markdown.\n\n"
        f"Incorrect output:\n{failed_content}"
    )
    response = _llm.invoke([HumanMessage(content=fix_prompt)])
    return response.content


# ── Public API ─────────────────────────────────────────────────────────────────


def invoke_extraction(text: str) -> CvExtraction:
    """
    Call the LLM and parse its JSON response into a CvExtraction instance.

    Error handling:
    - Network/timeout failures: retried up to llm_max_retries times.
    - CircuitOpenError: re-raised immediately → ERROR in the pipeline.
    - OutputParserException: if output_fixing_enabled, attempts self-correction
      up to output_fixing_max_retries times before re-raising → BLOCK in the pipeline.
    """
    _breaker.check()
    last_err: Exception | None = None

    for attempt in range(1, settings.llm_max_retries + 1):
        _breaker.check()
        try:
            content = _llm.invoke(_build_messages(text)).content

            try:
                result = _parser.parse(content)
            except OutputParserException as parse_exc:
                if not settings.output_fixing_enabled:
                    raise
                logger.warning(
                    "LLM output parse failed, attempting self-correction: %s", parse_exc
                )
                for fix_n in range(settings.output_fixing_max_retries):
                    content = _self_correct(content, str(parse_exc))
                    try:
                        result = _parser.parse(content)
                        logger.info("Self-correction succeeded on attempt %d", fix_n + 1)
                        break
                    except OutputParserException as next_exc:
                        parse_exc = next_exc
                        if fix_n == settings.output_fixing_max_retries - 1:
                            raise
                else:
                    raise  # should not reach here, but keeps mypy happy

            _breaker.record_success()
            return result

        except CircuitOpenError:
            raise
        except OutputParserException:
            raise
        except Exception as exc:
            last_err = exc
            _breaker.record_failure()
            logger.warning(
                "LLM attempt %d/%d failed: %s", attempt, settings.llm_max_retries, exc
            )
            if attempt < settings.llm_max_retries:
                time.sleep(settings.llm_retry_delay * attempt)

    raise RuntimeError(
        f"LLM failed after {settings.llm_max_retries} attempts"
    ) from last_err
