from __future__ import annotations

import contextlib
import logging
import time
from collections.abc import Generator
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)


@dataclass
class Span:
    name: str
    trace_id: str
    span_id: str
    parent_id: str | None
    start_ns: int = field(default_factory=time.monotonic_ns)
    attributes: dict = field(default_factory=dict)
    _ended: bool = field(default=False, init=False, repr=False)

    def set_attribute(self, key: str, value: object) -> None:
        self.attributes[key] = value

    def end(self) -> None:
        if not self._ended:
            elapsed_ms = (time.monotonic_ns() - self.start_ns) / 1_000_000
            logger.debug(
                "span name=%s trace_id=%s elapsed_ms=%.2f attrs=%s",
                self.name,
                self.trace_id,
                elapsed_ms,
                self.attributes,
            )
            self._ended = True


class Tracer:
    """
    Minimal no-op tracer that logs spans at DEBUG level.

    To use OpenTelemetry: replace this class with an OTEL SDK Tracer obtained
    via `opentelemetry.trace.get_tracer(__name__)` and configure an exporter
    (Jaeger, Zipkin, OTLP) in the startup routine.
    """

    def __init__(self) -> None:
        import secrets
        self._secret = secrets.token_hex

    @contextlib.contextmanager
    def start_span(
        self,
        name: str,
        parent: Span | None = None,
        attributes: dict | None = None,
    ) -> Generator[Span, None, None]:
        import secrets

        span = Span(
            name=name,
            trace_id=parent.trace_id if parent else secrets.token_hex(16),
            span_id=secrets.token_hex(8),
            parent_id=parent.span_id if parent else None,
            attributes=attributes or {},
        )
        try:
            yield span
        except Exception:
            span.set_attribute("error", True)
            raise
        finally:
            span.end()


tracer = Tracer()
