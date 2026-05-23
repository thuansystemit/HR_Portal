from __future__ import annotations

import logging
import uuid
from functools import lru_cache

from app.config import settings

logger = logging.getLogger(__name__)


@lru_cache(maxsize=1)
def _get_client():
    from qdrant_client import QdrantClient
    return QdrantClient(url=settings.vector_store_url, timeout=10)


def ensure_collection(vector_size: int) -> None:
    from qdrant_client.models import Distance, VectorParams

    client = _get_client()
    col = settings.vector_store_collection

    existing = {c.name for c in client.get_collections().collections}
    if col not in existing:
        client.create_collection(
            collection_name=col,
            vectors_config=VectorParams(size=vector_size, distance=Distance.COSINE),
        )
        logger.info("Created Qdrant collection '%s' (dim=%d)", col, vector_size)
    else:
        logger.debug("Qdrant collection '%s' already exists", col)


def upsert_chunks(
    document_id: str,
    category_id: str,
    document_type: str,
    chunks: list,          # list[Chunk] from chunking.py
    vectors: list[list[float]],
) -> None:
    from qdrant_client.models import PointStruct

    client = _get_client()
    points = [
        PointStruct(
            id=str(uuid.uuid5(uuid.NAMESPACE_URL, f"{document_id}:{chunk.index}")),
            vector=vec,
            payload={
                "document_id": document_id,
                "category_id": category_id,
                "document_type": document_type,
                "section": chunk.section,
                "chunk_index": chunk.index,
                "char_start": chunk.char_start,
                "char_end": chunk.char_end,
                "text": chunk.text[:1000],  # store first 1k chars for retrieval preview
            },
        )
        for chunk, vec in zip(chunks, vectors)
    ]
    client.upsert(collection_name=settings.vector_store_collection, points=points)
    logger.info("Upserted %d chunk(s) for document %s", len(points), document_id)


def delete_document(document_id: str) -> None:
    from qdrant_client.models import Filter, FieldCondition, MatchValue

    client = _get_client()
    client.delete(
        collection_name=settings.vector_store_collection,
        points_selector=Filter(
            must=[FieldCondition(key="document_id", match=MatchValue(value=document_id))]
        ),
    )
    logger.info("Deleted vectors for document %s", document_id)


def search(
    query_vector: list[float],
    document_type: str | None = None,
    category_id: str | None = None,
    top_k: int = 10,
) -> list[dict]:
    from qdrant_client.models import Filter, FieldCondition, MatchValue

    client = _get_client()

    must_conditions = []
    if document_type:
        must_conditions.append(
            FieldCondition(key="document_type", match=MatchValue(value=document_type))
        )
    if category_id:
        must_conditions.append(
            FieldCondition(key="category_id", match=MatchValue(value=category_id))
        )

    query_filter = Filter(must=must_conditions) if must_conditions else None

    hits = client.search(
        collection_name=settings.vector_store_collection,
        query_vector=query_vector,
        query_filter=query_filter,
        limit=top_k,
        with_payload=True,
    )

    return [
        {
            "document_id": hit.payload.get("document_id"),
            "category_id": hit.payload.get("category_id"),
            "document_type": hit.payload.get("document_type"),
            "section": hit.payload.get("section"),
            "chunk_index": hit.payload.get("chunk_index"),
            "text": hit.payload.get("text"),
            "score": round(hit.score, 4),
        }
        for hit in hits
    ]
