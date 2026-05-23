from __future__ import annotations

import re
from dataclasses import dataclass

# ── CV section heading patterns ──────────────────────────────────────────────

_CV_SECTION_RE = re.compile(
    r"^(?P<heading>"
    r"SUMMARY|PROFILE|ABOUT\s+ME|CAREER\s+OBJECTIVE|PROFESSIONAL\s+SUMMARY|INTRODUCTION|"
    r"WORK\s+EXPERIENCE|PROFESSIONAL\s+EXPERIENCE|EMPLOYMENT\s+HISTORY|WORK\s+HISTORY|EXPERIENCE|CAREER|"
    r"EDUCATION|ACADEMIC\s+BACKGROUND|ACADEMIC|QUALIFICATIONS|"
    r"SKILLS|TECHNICAL\s+SKILLS|CORE\s+COMPETENCIES|KEY\s+SKILLS|COMPETENCIES|EXPERTISE|"
    r"CERTIFICATIONS?|LICEN[SC]ES?|CREDENTIALS|"
    r"PROJECTS?|PERSONAL\s+PROJECTS?|SIDE\s+PROJECTS?|"
    r"LANGUAGES?|LANGUAGE\s+SKILLS|"
    r"PUBLICATIONS?|RESEARCH|PAPERS?|"
    r"AWARDS?|HONORS?|ACHIEVEMENTS?|ACCOMPLISHMENTS?|"
    r"REFERENCES?|REFEREES?|"
    r"VOLUNTEERING?|COMMUNITY|"
    r"INTERESTS?|HOBBIES|ACTIVITIES"
    r")[\s:.\-]*$",
    re.IGNORECASE | re.MULTILINE,
)

_SECTION_NAMES: dict[str, str] = {
    "summary": "summary", "profile": "summary", "about me": "summary",
    "career objective": "summary", "professional summary": "summary", "introduction": "summary",
    "work experience": "experience", "professional experience": "experience",
    "employment history": "experience", "work history": "experience",
    "experience": "experience", "career": "experience",
    "education": "education", "academic background": "education",
    "academic": "education", "qualifications": "education",
    "skills": "skills", "technical skills": "skills", "core competencies": "skills",
    "key skills": "skills", "competencies": "skills", "expertise": "skills",
    "certifications": "certifications", "certification": "certifications",
    "licences": "certifications", "licenses": "certifications", "credentials": "certifications",
    "projects": "projects", "project": "projects",
    "personal projects": "projects", "side projects": "projects",
    "languages": "languages", "language": "languages", "language skills": "languages",
    "publications": "publications", "publication": "publications",
    "research": "publications", "papers": "publications",
    "awards": "awards", "honors": "awards", "achievements": "awards",
    "accomplishments": "awards",
    "references": "references", "referees": "references",
    "volunteering": "volunteer", "volunteer": "volunteer", "community": "volunteer",
    "interests": "interests", "hobbies": "interests", "activities": "interests",
}

# ── Invoice section patterns ──────────────────────────────────────────────────

_INVOICE_ITEMS_RE = re.compile(
    r"(?i)(DESCRIPTION|ITEM|SERVICE|PRODUCT|QTY|QUANTITY|UNIT\s+PRICE|AMOUNT|RATE)",
)
_INVOICE_FOOTER_RE = re.compile(
    r"(?i)^(SUBTOTAL|SUB-TOTAL|TAX|VAT|DISCOUNT|TOTAL|AMOUNT\s+DUE|BALANCE\s+DUE|"
    r"NOTES?|TERMS?|PAYMENT\s+TERMS?|BANK\s+DETAILS?|THANK\s+YOU)",
    re.MULTILINE,
)


@dataclass
class Chunk:
    text: str
    section: str
    index: int
    char_start: int
    char_end: int


# ── CV chunker ────────────────────────────────────────────────────────────────


def _normalise_heading(raw: str) -> str:
    key = raw.strip().rstrip(":.").lower()
    key = re.sub(r"\s+", " ", key)
    return _SECTION_NAMES.get(key, key)


def chunk_cv(text: str) -> list[Chunk]:
    lines = text.split("\n")
    sections: list[tuple[str, int]] = []  # (section_name, line_index)

    for i, line in enumerate(lines):
        m = _CV_SECTION_RE.match(line.strip())
        if m:
            sections.append((_normalise_heading(m.group("heading")), i))

    if not sections:
        # No detectable sections — return the whole text as one chunk
        return [Chunk(text=text, section="body", index=0, char_start=0, char_end=len(text))]

    chunks: list[Chunk] = []
    char_offsets = _line_offsets(lines)

    for idx, (section, line_idx) in enumerate(sections):
        start_line = line_idx
        end_line = sections[idx + 1][1] if idx + 1 < len(sections) else len(lines)
        body = "\n".join(lines[start_line:end_line]).strip()
        if not body:
            continue
        char_start = char_offsets[start_line]
        char_end = char_offsets[min(end_line, len(lines) - 1)] + len(lines[min(end_line, len(lines) - 1)])
        chunks.append(Chunk(text=body, section=section, index=len(chunks),
                            char_start=char_start, char_end=char_end))

    return chunks or [Chunk(text=text, section="body", index=0, char_start=0, char_end=len(text))]


# ── Invoice chunker ───────────────────────────────────────────────────────────


def chunk_invoice(text: str) -> list[Chunk]:
    lines = text.split("\n")
    header_end = len(lines)
    items_end = len(lines)

    # Find where the line-items table starts
    for i, line in enumerate(lines):
        if _INVOICE_ITEMS_RE.search(line):
            header_end = i
            break

    # Find where the footer (totals/notes) starts
    for i in range(header_end, len(lines)):
        if _INVOICE_FOOTER_RE.match(lines[i].strip()):
            items_end = i
            break

    char_offsets = _line_offsets(lines)
    chunks: list[Chunk] = []

    def _add(start: int, end: int, section: str) -> None:
        body = "\n".join(lines[start:end]).strip()
        if not body:
            return
        cs = char_offsets[start]
        ce = char_offsets[min(end, len(lines) - 1)] + len(lines[min(end, len(lines) - 1)])
        chunks.append(Chunk(text=body, section=section, index=len(chunks),
                            char_start=cs, char_end=ce))

    _add(0, header_end, "header")
    _add(header_end, items_end, "line_items")
    _add(items_end, len(lines), "footer")

    return chunks or [Chunk(text=text, section="body", index=0, char_start=0, char_end=len(text))]


# ── Shared helper ─────────────────────────────────────────────────────────────


def _line_offsets(lines: list[str]) -> list[int]:
    offsets = [0]
    for line in lines[:-1]:
        offsets.append(offsets[-1] + len(line) + 1)  # +1 for \n
    return offsets


def chunk_document(text: str, document_type: str) -> list[Chunk]:
    if document_type == "INVOICE":
        return chunk_invoice(text)
    return chunk_cv(text)
