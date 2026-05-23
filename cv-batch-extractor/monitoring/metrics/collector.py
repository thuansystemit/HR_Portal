from __future__ import annotations

import threading
import time
from collections import defaultdict
from dataclasses import dataclass, field


@dataclass
class _Counter:
    value: int = 0
    _lock: threading.Lock = field(default_factory=threading.Lock, repr=False)

    def inc(self, n: int = 1) -> None:
        with self._lock:
            self.value += n


@dataclass
class _Histogram:
    buckets: list[float]
    _counts: list[int] = field(init=False)
    _sum: float = field(default=0.0, init=False)
    _total: int = field(default=0, init=False)
    _lock: threading.Lock = field(default_factory=threading.Lock, repr=False)

    def __post_init__(self) -> None:
        self._counts = [0] * (len(self.buckets) + 1)

    def observe(self, value: float) -> None:
        with self._lock:
            self._sum += value
            self._total += 1
            for i, bound in enumerate(self.buckets):
                if value <= bound:
                    self._counts[i] += 1
                    return
            self._counts[-1] += 1

    def snapshot(self) -> dict:
        with self._lock:
            return {
                "buckets": {str(b): c for b, c in zip(self.buckets, self._counts)},
                "sum": self._sum,
                "count": self._total,
            }


class MetricsCollector:
    """
    In-process metrics store. Replace with Prometheus client or OTEL SDK
    by swapping the backend without changing call sites.
    """

    def __init__(self) -> None:
        self._counters: dict[str, _Counter] = defaultdict(lambda: _Counter())
        self._histograms: dict[str, _Histogram] = {}

    # ── counters ───────────────────────────────────────────────────────────

    def inc(self, name: str, n: int = 1) -> None:
        self._counters[name].inc(n)

    def counter(self, name: str) -> int:
        return self._counters[name].value

    # ── histograms ─────────────────────────────────────────────────────────

    def register_histogram(
        self, name: str, buckets: list[float] | None = None
    ) -> None:
        if name not in self._histograms:
            self._histograms[name] = _Histogram(
                buckets=buckets or [0.1, 0.5, 1.0, 5.0, 10.0, 30.0, 60.0]
            )

    def observe(self, name: str, value: float) -> None:
        if name not in self._histograms:
            self.register_histogram(name)
        self._histograms[name].observe(value)

    def snapshot(self) -> dict:
        return {
            "counters": {k: v.value for k, v in self._counters.items()},
            "histograms": {k: v.snapshot() for k, v in self._histograms.items()},
        }


# module-level singleton
metrics = MetricsCollector()
metrics.register_histogram("document_processing_seconds")
metrics.register_histogram("ocr_seconds")
metrics.register_histogram("llm_seconds")
