"""SA-9: Tokenise PII in CV text before sending to LLM.

Replaces email addresses, phone numbers, and personal profile URLs with
reversible tokens (e.g. __PII_EMAIL_0__) so the LLM never processes raw
personal identifiers.  The PiiRestoreGuard in the output pipeline swaps
tokens back after extraction.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field

from app.guardrails.base import GuardrailReport, PipelineContext

# --- PII detection patterns ---------------------------------------------------

# RFC-5321 simplified — handles the vast majority of real addresses
_EMAIL_RE = re.compile(r'\b[\w.%+\-]+@[\w\-]+(?:\.[A-Za-z]{2,})+\b')

# Matches common formatted phone numbers; deliberately conservative to avoid
# false-positives on year ranges (e.g. 2019–2023) or GPA values.
#   +84 123 456 789  |  +1-800-555-1234  |  (800) 555-1234  |  0123-456-789
_PHONE_RE = re.compile(
    r'(?:'
    r'\+\d{1,3}[\s.\-]?\(?\d{1,4}\)?[\s.\-]\d{2,4}[\s.\-]\d{3,4}(?:[\s.\-]\d{1,4})?'
    r'|'
    r'\(?\d{3}\)?[\s.\-]\d{3}[\s.\-]\d{4}'
    r')'
)

# LinkedIn and GitHub profile URLs — order matters (specific before generic)
_LINKEDIN_RE = re.compile(r'https?://(?:www\.)?linkedin\.com/in/[\w%\-./]+', re.I)
_GITHUB_RE   = re.compile(r'https?://(?:www\.)?github\.com/[\w%\-./]+', re.I)

# Ordered list of (pattern, token-kind) pairs — longer/more-specific first
_PATTERNS: list[tuple[re.Pattern[str], str]] = [
    (_LINKEDIN_RE, "LINKEDIN"),
    (_GITHUB_RE,   "GITHUB"),
    (_EMAIL_RE,    "EMAIL"),
    (_PHONE_RE,    "PHONE"),
]


# --- Tokenizer ----------------------------------------------------------------

@dataclass
class PiiTokenizer:
    """Bidirectional PII ↔ token mapper.  One instance per document."""
    _map: dict[str, str] = field(default_factory=dict)        # token  → actual
    _kind_counts: dict[str, int] = field(default_factory=dict)

    def _next_token(self, kind: str) -> str:
        n = self._kind_counts.get(kind, 0)
        self._kind_counts[kind] = n + 1
        return f"__PII_{kind}_{n}__"

    def mask(self, text: str) -> str:
        """Return a copy of *text* with all detected PII replaced by tokens."""
        for pattern, kind in _PATTERNS:
            def replacer(m: re.Match, _kind: str = kind) -> str:
                tok = self._next_token(_kind)
                self._map[tok] = m.group(0)
                return tok
            text = pattern.sub(replacer, text)
        return text

    def restore(self, value: str | None) -> str | None:
        """Substitute any tokens in *value* with their original strings."""
        if not value:
            return value
        for tok, actual in self._map.items():
            value = value.replace(tok, actual)
        return value

    def first_of_kind(self, kind: str) -> str | None:
        """Return the first masked value of *kind*, or None if none found."""
        prefix = f"__PII_{kind}_"
        for tok, actual in self._map.items():
            if tok.startswith(prefix):
                return actual
        return None

    @property
    def total_masked(self) -> int:
        return len(self._map)


# --- Guard --------------------------------------------------------------------

class PiiMaskGuard:
    """Input guardrail: tokenise PII in raw_text/prompt_text before LLM call."""

    def run(self, ctx: PipelineContext) -> GuardrailReport:
        tokenizer = PiiTokenizer()

        if ctx.raw_text:
            ctx.raw_text = tokenizer.mask(ctx.raw_text)
        if ctx.prompt_text:
            ctx.prompt_text = tokenizer.mask(ctx.prompt_text)

        ctx.pii_tokenizer = tokenizer  # type: ignore[attr-defined]

        return GuardrailReport(
            guard="PiiMaskGuard",
            status="PASS",
            metadata={"masked_tokens": tokenizer.total_masked},
        )
