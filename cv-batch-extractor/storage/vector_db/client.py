from __future__ import annotations

import logging
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass
class VectorRecord:
    id: str
    vector: list[float]
    payload: dict


class VectorDBClient:
    """
    Stores CV embedding vectors for semantic search.

    TODO: Choose a backend — Qdrant, Weaviate, Milvus, or pgvector.
    TODO: Install corresponding client: `pip install qdrant-client` (or equivalent).
    TODO: Generate embeddings via sentence-transformers or OpenAI embeddings API.
    TODO: Index on document_id + category_id for filtered retrieval.
    TODO: Implement upsert() so re-processed documents overwrite old vectors.
    """

    def upsert(self, record: VectorRecord) -> None:
        logger.debug("VectorDB upsert (stub) id=%s vector_dim=%d", record.id, len(record.vector))

    def search(self, vector: list[float], top_k: int = 10) -> list[VectorRecord]:
        logger.debug("VectorDB search (stub) top_k=%d", top_k)
        return []

    def delete(self, record_id: str) -> None:
        logger.debug("VectorDB delete (stub) id=%s", record_id)
