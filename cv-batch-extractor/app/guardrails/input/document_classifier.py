import re

from app.guardrails.base import GuardrailReport, PipelineContext

_CV_TERMS = {
    "curriculum vitae", "resume", "work experience", "employment history",
    "professional experience", "professional summary", "career objective",
    "education", "qualifications", "certifications", "skills", "references",
    "objective", "employer", "position", "job title",
}

_INVOICE_TERMS = {
    "invoice", "invoice number", "invoice date", "bill to", "ship to",
    "purchase order", "vendor", "buyer", "payment terms", "due date",
    "subtotal", "tax", "total amount", "amount due", "line item",
    "description", "quantity", "unit price",
}

_MIN_SCORE = 0.15   # minimum fraction of keywords that must match before we trust the score
_MISMATCH_MARGIN = 0.20  # opposite type must outscore declared type by this margin to warn


def _score(text: str, terms: set[str]) -> float:
    lower = text.lower()
    hits = sum(1 for t in terms if re.search(r"\b" + re.escape(t) + r"\b", lower))
    return hits / len(terms)


class DocumentClassifierGuard:
    """
    Keyword-scores the extracted text to verify it matches the path-declared type.
    Issues WARN when the content strongly resembles the *other* type.
    Never BLOCKs — the path declaration is authoritative; this is a quality signal only.
    """

    def run(self, ctx: PipelineContext) -> GuardrailReport:
        text = ctx.prompt_text or ctx.raw_text or ""
        if not text.strip():
            return GuardrailReport(guard="document_classifier", status="PASS")

        cv_score = _score(text, _CV_TERMS)
        inv_score = _score(text, _INVOICE_TERMS)
        declared = ctx.document_type  # "CV" or "INVOICE"

        dominant = "CV" if cv_score >= inv_score else "INVOICE"
        dominant_score = max(cv_score, inv_score)
        opposite_score = min(cv_score, inv_score)

        meta = {
            "declared_type": declared,
            "cv_score": round(cv_score, 3),
            "invoice_score": round(inv_score, 3),
        }

        if (
            dominant != declared
            and dominant_score >= _MIN_SCORE
            and (dominant_score - opposite_score) >= _MISMATCH_MARGIN
        ):
            return GuardrailReport(
                guard="document_classifier",
                status="WARN",
                reason=(
                    f"document_classifier: content looks like {dominant} "
                    f"(score {dominant_score:.2f}) but declared type is {declared} "
                    f"(score {opposite_score:.2f}) — verify file is in the correct folder"
                ),
                metadata=meta,
            )

        return GuardrailReport(guard="document_classifier", status="PASS", metadata=meta)
