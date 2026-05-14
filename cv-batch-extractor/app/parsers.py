import logging

import fitz
import pytesseract
from docx import Document
from PIL import Image

logger = logging.getLogger(__name__)

SUPPORTED_EXTENSIONS = {".pdf", ".docx", ".doc", ".jpg", ".jpeg", ".png", ".tiff", ".bmp"}


def pdf_to_text(path: str) -> str:
    doc = fitz.open(path)
    return "\n".join(page.get_text() for page in doc)


def docx_to_text(path: str) -> str:
    doc = Document(path)
    return "\n".join(p.text for p in doc.paragraphs)


def image_to_text(path: str) -> str:
    img = Image.open(path)
    return pytesseract.image_to_string(img)


def extract_text(path: str) -> str:
    lower = path.lower()
    if lower.endswith(".pdf"):
        return pdf_to_text(path)
    if lower.endswith(".docx") or lower.endswith(".doc"):
        return docx_to_text(path)
    return image_to_text(path)
