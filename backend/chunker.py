"""
Turn raw exam / answer-key text into chunk dicts that match the schema
indexer.py already consumes (chunk_id, course_code, year, semester, moed,
chunk_type, page_start, source_file, chunk_text, ...).

The historical chunks in data/chunks/ were produced by a separate offline
pipeline. This module is the backend's own chunker so teachers can upload an
exam + answer key at runtime and have it indexed the same way.

Design goals:
  - Deterministic: same input + same metadata -> same chunk_ids (so re-uploading
    upserts instead of duplicating in Chroma).
  - Schema-compatible with indexer.DocumentIndexer so retrieval treats uploaded
    chunks identically to the pre-built corpus.
  - Reliable indexing metadata comes from the uploader (form fields); only the
    per-chunk fields (question_number, chunk_type) are derived from content.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Optional

# A question starts a line like "1.", "12)", "1 ." — 1-2 digits then . or ).
_QUESTION_RE = re.compile(r"^\s*(\d{1,2})\s*[.)]\s+", re.MULTILINE)
# OCR page separators emitted by ocr.ocr_pages_sync(): "--- PAGE 3 ---".
_PAGE_RE = re.compile(r"^-{2,}\s*PAGE\s+(\d+)\s*-{2,}\s*$", re.MULTILINE | re.IGNORECASE)
# Reading-passage markers ("TEXT 1", "TEXT 2").
_TEXT_MARKER_RE = re.compile(r"^\s*TEXT\s+\d+\s*$", re.MULTILINE | re.IGNORECASE)
# Signals that a document carries the official answers, not just the questions.
_ANSWER_SIGNALS = (
    "answer key",
    "possible answer",
    "rubric",
    "marking scheme",
    "model answer",
)
# Filled-in blank like "___ Upsetting elections ___" (text between underscores).
_FILLED_BLANK_RE = re.compile(r"_{2,}\s*\S.*?\S\s*_{2,}")
# Per-question point allocation, e.g. "(6 points)".
_POINTS_RE = re.compile(r"\(\s*\d+\s*points?\s*\)", re.IGNORECASE)


@dataclass
class ChunkMeta:
    """Reliable indexing metadata supplied by the uploader (form fields)."""
    course_code: str
    year: str
    semester: str
    moed: str
    version: str = "v1"

    @property
    def doc_id(self) -> str:
        return f"{self.course_code}_{self.year}_{self.semester}_{self.moed}_{self.version}"


def _strip_page_markers(text: str) -> str:
    return _PAGE_RE.sub("\n", text)


def looks_like_answer_key(text: str) -> bool:
    """Heuristic: does this text contain the official answers (vs just questions)?"""
    low = text.lower()
    if any(sig in low for sig in _ANSWER_SIGNALS):
        return True
    # Many filled-in blanks is a strong signal of a completed answer key.
    return len(_FILLED_BLANK_RE.findall(text)) >= 3


def _split_questions(text: str) -> tuple[str, list[tuple[str, str]]]:
    """Split text on question-number boundaries.

    Returns (preamble, [(question_number, body), ...]) where preamble is any
    text before the first numbered question (title, instructions, etc.).
    """
    matches = list(_QUESTION_RE.finditer(text))
    if not matches:
        return text.strip(), []

    preamble = text[: matches[0].start()].strip()
    items: list[tuple[str, str]] = []
    for i, m in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        number = m.group(1)
        body = text[m.start():end].strip()
        items.append((number, body))
    return preamble, items


def _make_chunk(meta: ChunkMeta, source_file: str, idx: int, chunk_type: str,
                chunk_text: str, question_number: str = "",
                section_type: str = "general", page_start: int = 1) -> dict:
    qn = f"q{question_number}" if question_number else f"chunk_{idx:04d}"
    return {
        "chunk_id": f"{meta.doc_id}_{qn}",
        "doc_id": meta.doc_id,
        "source_file": source_file,
        "chunk_type": chunk_type,
        "section_type": section_type,
        "question_number": question_number,
        "page_start": page_start,
        "chunk_index": idx,
        "course_code": meta.course_code,
        "year": meta.year,
        "semester": meta.semester,
        "moed": meta.moed,
        "version": meta.version,
        "chunk_text": chunk_text,
    }


def _chunk_single(text: str, meta: ChunkMeta, source_file: str,
                  force_type: Optional[str] = None) -> list[dict]:
    """Chunk one document (combined questions+answers, or questions-only)."""
    text = _strip_page_markers(text)
    is_key = force_type == "answer_key" or (force_type is None and looks_like_answer_key(text))
    q_type = "answer_key" if is_key else "question"

    # Separate reading passages (everything from the first TEXT marker on) from
    # the question section so passages become their own "reading" chunks.
    reading_text = ""
    tm = _TEXT_MARKER_RE.search(text)
    if tm:
        reading_text = text[tm.start():].strip()
        text = text[: tm.start()]

    preamble, items = _split_questions(text)

    chunks: list[dict] = []
    idx = 1
    if preamble:
        chunks.append(_make_chunk(meta, source_file, idx, "instruction", preamble))
        idx += 1
    for number, body in items:
        chunks.append(_make_chunk(meta, source_file, idx, q_type, body,
                                  question_number=number))
        idx += 1
    if reading_text:
        chunks.append(_make_chunk(meta, source_file, idx, "reading", reading_text))
        idx += 1
    return chunks


def _chunk_paired(questions_text: str, answer_key_text: str, meta: ChunkMeta,
                  source_file: str) -> list[dict]:
    """Two separate uploads: emit a 'question' chunk and an 'answer_key' chunk
    per question, both carrying the same question_number for metadata filtering."""
    q_chunks = _chunk_single(questions_text, meta, source_file, force_type="question")
    a_chunks = _chunk_single(answer_key_text, meta, source_file, force_type="answer_key")

    # Re-key the answer chunks so question/answer ids don't collide.
    for c in a_chunks:
        c["chunk_id"] = c["chunk_id"] + "_key"
    return q_chunks + a_chunks


def build_chunks(meta: ChunkMeta, source_file: str,
                 questions_text: Optional[str] = None,
                 answer_key_text: Optional[str] = None) -> list[dict]:
    """Auto-detect mode and return schema-compatible chunk dicts.

    - both texts present  -> paired mode (separate question + answer_key chunks)
    - one text present    -> single-doc mode (answer-key vs questions auto-detected)
    """
    q = (questions_text or "").strip()
    a = (answer_key_text or "").strip()

    if q and a:
        return _chunk_paired(q, a, meta, source_file)
    if a and not q:
        return _chunk_single(a, meta, source_file, force_type="answer_key")
    if q and not a:
        return _chunk_single(q, meta, source_file)
    raise ValueError("build_chunks requires at least one of questions_text / answer_key_text")
