from monitoring.logging.setup import configure as configure_logging

configure_logging(level="INFO", fmt="text")

from workflow.orchestration import WorkerPool
from ingestion.file_watcher.watcher import start as start_watcher

if __name__ == "__main__":
    pool = WorkerPool()
    try:
        start_watcher(pool.submit)
    finally:
        pool.shutdown()
