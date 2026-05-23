from __future__ import annotations

import logging
import sys
from typing import Literal


def configure(
    level: Literal["DEBUG", "INFO", "WARNING", "ERROR"] = "INFO",
    fmt: Literal["json", "text"] = "text",
) -> None:
    """
    Call once at process startup before any other imports that log.

    fmt="json" emits structured JSON lines suitable for Logstash / Cloud Logging.
    fmt="text" emits human-readable lines for local development.
    """
    root = logging.getLogger()
    root.setLevel(getattr(logging, level))

    if root.handlers:
        root.handlers.clear()

    handler = logging.StreamHandler(sys.stdout)
    handler.setLevel(getattr(logging, level))

    if fmt == "json":
        handler.setFormatter(_JsonFormatter())
    else:
        handler.setFormatter(
            logging.Formatter(
                fmt="%(asctime)s %(levelname)-8s %(name)s — %(message)s",
                datefmt="%Y-%m-%dT%H:%M:%S",
            )
        )

    root.addHandler(handler)


class _JsonFormatter(logging.Formatter):
    import json as _json

    def format(self, record: logging.LogRecord) -> str:
        import json
        from datetime import datetime, timezone

        payload: dict = {
            "ts": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "msg": record.getMessage(),
        }
        if record.exc_info:
            payload["exc"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=False)
