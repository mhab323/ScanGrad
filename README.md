# ScanGrad API

FastAPI backend for AI-powered exam grading. Pairs with an Android client that uploads handwritten exam images; the API extracts the text, retrieves relevant rubric entries from a vector store, and asks Claude to produce a score and feedback.

## Stack

- **FastAPI** — HTTP layer
- **pytesseract** — OCR for handwritten exam images
- **sentence-transformers** (`all-MiniLM-L6-v2`) — embeddings
- **ChromaDB** — local persistent vector store for grading rules
- **Anthropic Claude** (`claude-opus-4-6`) — grading model

## Pipeline

`POST /api/evaluate` → download image from `image_url` → OCR → embed submission → query ChromaDB for top-K rubric entries filtered by `course_code` → prompt Claude with rules + submission → parse JSON response → return `{final_score, feedback, confidence_level}`.

> **Note:** `main.py` currently returns hardcoded mock responses keyed off `submission_id` (`good`, `mid`, anything else). The real pipeline lives in `grader.py` and is not yet wired into the endpoint.

## Files

| File | Purpose |
| --- | --- |
| `main.py` | FastAPI app, `/health` and `/api/evaluate` endpoints |
| `grader.py` | End-to-end grading pipeline (OCR → embed → RAG → Claude) |
| `ocr.py` | Downloads image from URL and runs Tesseract |
| `embedder.py` | Wraps sentence-transformers encoder |
| `rag.py` | ChromaDB client; `retrieve_rules` and `add_rule` |
| `ingest_rules.py` | One-time script to load rubric entries into ChromaDB |
| `models.py` | `EvaluationRequest` / `EvaluationResponse` Pydantic models |

## Setup

```bash
pip install -r requirements.txt
```

Install [Tesseract OCR](https://github.com/UB-Mannheim/tesseract/wiki). The path is hardcoded in `ocr.py` to `C:\Program Files\Tesseract-OCR\tesseract.exe` — update for your platform.

Create a `.env` with:

```
ANTHROPIC_API_KEY=sk-ant-...
```

## Run

```bash
uvicorn main:app --reload
```

`GET /health` → `{"status": "ok"}`.

## Ingesting rubrics

Edit the `rules` list in `ingest_rules.py` (each entry needs `id`, `text`, `course_code`), then:

```bash
python ingest_rules.py
```

This persists embeddings to `./chroma_db/`.

## Request shape

```json
POST /api/evaluate
{
  "submission_id": "abc123",
  "course_code": "ENG-ADV-B",
  "extracted_text": "...",
  "image_url": "https://..."
}
```
