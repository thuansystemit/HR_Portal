import logging
import threading

from app.config import settings
from app.watcher import start
from app.worker import WorkerPool

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-8s %(name)s — %(message)s",
    datefmt="%Y-%m-%dT%H:%M:%S",
)

logger = logging.getLogger(__name__)


def _start_search_api() -> None:
    import uvicorn
    from app.api import app
    logger.info("Starting search API on %s:%d", settings.search_api_host, settings.search_api_port)
    uvicorn.run(
        app,
        host=settings.search_api_host,
        port=settings.search_api_port,
        log_level="warning",
    )


if __name__ == "__main__":
    if settings.search_api_enabled:
        api_thread = threading.Thread(target=_start_search_api, daemon=True, name="search-api")
        api_thread.start()

    pool = WorkerPool()
    try:
        start(pool)
    finally:
        pool.shutdown()
