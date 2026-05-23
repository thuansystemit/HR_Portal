FROM python:3.11-slim

# ── system deps ────────────────────────────────────────────────────────────
# tesseract-ocr   → TesseractAdapter + built-in image OCR fallback
# libmagic1       → python-magic MIME detection
# libgl1          → OpenCV headless (deskew/denoise preprocessing)
# libreoffice     → FormatConverter (DOC/DOCX → PDF)
# curl + nodejs   → LiteParse CLI installer
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    libmagic1 \
    libgl1 \
    libreoffice \
    curl \
    && curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

RUN npm install -g @llamaindex/liteparse

# ── python deps ────────────────────────────────────────────────────────────
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ── application code ───────────────────────────────────────────────────────
COPY . .

CMD ["python", "main.py"]
