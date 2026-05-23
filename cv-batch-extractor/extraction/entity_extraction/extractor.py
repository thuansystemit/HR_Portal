from __future__ import annotations

import logging
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)


@dataclass
class EntityResult:
    persons: list[str] = field(default_factory=list)
    organisations: list[str] = field(default_factory=list)
    locations: list[str] = field(default_factory=list)
    dates: list[str] = field(default_factory=list)
    emails: list[str] = field(default_factory=list)
    phones: list[str] = field(default_factory=list)


class EntityExtractor:
    """
    Named-entity recognition over raw document text.

    TODO: Integrate spaCy (en_core_web_sm) or a fine-tuned NER model.
    TODO: Use entity results to pre-populate high-confidence fields before
          LLM extraction, reducing hallucination risk.
    TODO: Add email + phone regex extraction as a deterministic baseline.
    """

    def extract(self, text: str) -> EntityResult:
        """Returns named entities found in the text."""
        import re
        result = EntityResult()

        email_re = re.compile(r"[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}")
        result.emails = email_re.findall(text)

        phone_re = re.compile(r"[\+\d][\d\s\-\(\)]{7,15}\d")
        result.phones = phone_re.findall(text)

        logger.debug(
            "Entity extraction found %d emails, %d phones",
            len(result.emails),
            len(result.phones),
        )
        return result
