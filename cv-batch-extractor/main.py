import logging

from app.watcher import start
from app.worker import WorkerPool

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-8s %(name)s — %(message)s",
    datefmt="%Y-%m-%dT%H:%M:%S",
)

if __name__ == "__main__":
    pool = WorkerPool()
    try:
        start(pool)
    finally:
        pool.shutdown()
