from __future__ import annotations

import logging

from domain.models import PipelineContext

logger = logging.getLogger(__name__)


class DataMapper:
    """
    Maps extracted CvExtraction fields to target system schemas.

    TODO: Implement mapping from CvExtraction → HR system candidate model.
    TODO: Support configurable field-mapping rules (e.g. YAML-defined mappings).
    TODO: Handle field aliasing (e.g. 'fullName' → 'candidate_name' in ATS).
    """

    def map(self, ctx: PipelineContext) -> dict:
        """Returns a dict ready for the backend API or database insert."""
        if ctx.cv_data is None:
            return {}

        return ctx.cv_data.model_dump(exclude_none=False)
