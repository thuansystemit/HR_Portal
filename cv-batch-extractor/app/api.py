from __future__ import annotations

import logging
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from app.config import settings

logger = logging.getLogger(__name__)

app = FastAPI(
    title="CV Batch Extractor — Search API",
    version="1.0.0",
    docs_url="/docs",
)


# ── Request / Response models ─────────────────────────────────────────────────

class SearchRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=1000)
    document_type: Optional[str] = Field(None, pattern="^(CV|INVOICE)$")
    category_id: Optional[str] = None
    top_k: int = Field(10, ge=1, le=50)


class SearchHit(BaseModel):
    document_id: str
    category_id: Optional[str]
    document_type: Optional[str]
    section: Optional[str]
    chunk_index: Optional[int]
    text: Optional[str]
    score: float


class SearchResponse(BaseModel):
    query: str
    total: int
    results: list[SearchHit]


# ── Endpoints ─────────────────────────────────────────────────────────────────

@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/api/v1/search", response_model=SearchResponse)
def search(req: SearchRequest) -> SearchResponse:
    if not settings.embedding_enabled:
        raise HTTPException(status_code=503, detail="Embedding pipeline is disabled")

    try:
        from app.embedding import embed_texts
        from app.vector_store import search as vs_search

        query_vectors = embed_texts([req.query])
        hits = vs_search(
            query_vector=query_vectors[0],
            document_type=req.document_type,
            category_id=req.category_id,
            top_k=req.top_k,
        )
        return SearchResponse(
            query=req.query,
            total=len(hits),
            results=[SearchHit(**h) for h in hits],
        )
    except Exception as exc:
        logger.exception("Search failed: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc))


@app.delete("/api/v1/documents/{document_id}/vectors")
def delete_vectors(document_id: str) -> dict:
    try:
        from app.vector_store import delete_document
        delete_document(document_id)
        return {"deleted": document_id}
    except Exception as exc:
        logger.exception("Delete vectors failed: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc))
