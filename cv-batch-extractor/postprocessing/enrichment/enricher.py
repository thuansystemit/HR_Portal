from __future__ import annotations

import logging

from domain.models import PipelineContext

logger = logging.getLogger(__name__)


class Enricher:
    """
    Enriches extracted CV data with external signals.

    TODO: Geocode city/country to lat/long for location-based search.
    TODO: Normalise job titles against a standard taxonomy (O*NET, ISCO-08).
    TODO: Resolve company names against a company database (Clearbit, LinkedIn).
    TODO: Tag technical skills against a skills ontology (ESCO, Stack Overflow tags).
    """

    def enrich(self, ctx: PipelineContext) -> None:
        """Mutates ctx.cv_data in-place with enrichment data."""
        if ctx.cv_data is None:
            return
        logger.debug("Enrichment (stub) for document %s", ctx.document_id)
