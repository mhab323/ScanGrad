from contextlib import asynccontextmanager

from config import load_environment, lifespan

load_environment()

from fastapi import FastAPI, HTTPException

from models import EvaluationRequest, EvaluationResponse, IngestRequest, IngestResponse
from ocr import extract_text_from_url
from generator import RAGGenerator
from chunker import ChunkMeta, build_chunks
from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_huggingface import HuggingFaceEmbeddings
from collections import Counter
import socket
import firebase_admin
from firebase_admin import credentials, firestore
import time

print("Initializing Vector Database and RAG Generator...")
# Must match indexer.py exactly: the corpus was embedded with normalized
# vectors, so queries AND newly-ingested chunks have to be normalized too,
# otherwise they live in an inconsistent distance space and retrieval degrades.
embedding_model = HuggingFaceEmbeddings(
    model_name="sentence-transformers/all-MiniLM-L6-v2",
    model_kwargs={"device": "cpu"},
    encode_kwargs={"normalize_embeddings": True},
)
vectorstore = Chroma(persist_directory="./chroma_db", embedding_function=embedding_model)
generator = RAGGenerator(vectorstore=vectorstore)

app = FastAPI(lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok", "message": "LangChain RAG Engine Online"}


@app.post("/api/evaluate", response_model=EvaluationResponse)
async def evaluate(request: EvaluationRequest):
    try:
        start_time = time.time()
        print(f"\n--- NEW REQUEST RECEIVED ---")

        submission_text = request.extracted_text

        if not submission_text and request.image_url:
            print("1. Starting Image Download and OCR (This might take a while)...")
            ocr_start = time.time()
            submission_text = await extract_text_from_url(request.image_url)
            print(f"   -> OCR Finished in {round(time.time() - ocr_start, 2)} seconds!")

        if not submission_text:
            raise HTTPException(status_code=400, detail="Could not extract text.")

        print("2. Starting AI Evaluation (Searching database and generating grade)...")
        rag_start = time.time()
        final_evaluation = generator.evaluate_submission(
            exam_question=request.exam_question,
            student_submission=submission_text,
            course_code=request.course_code
        )
        print(f"   -> AI Evaluation Finished in {round(time.time() - rag_start, 2)} seconds!")

        print(f"--- TOTAL TIME: {round(time.time() - start_time, 2)} seconds ---\n")
        return final_evaluation

    except Exception as e:
        print(f"ERROR: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


# Metadata fields persisted to Chroma. Mirrors indexer.py and adds the fields
# the Tier-2 metadata-filter retrieval plan needs (question_number, version).
# Chroma only accepts scalar metadata values (str/int/float/bool).
_INDEXED_META_FIELDS = (
    "chunk_id", "doc_id", "course_code", "year", "semester", "moed",
    "version", "chunk_type", "question_number", "page_start", "source_file",
)


async def _resolve_text(inline: str | None, url: str | None) -> str:
    """Return inline text if given, else OCR the URL, else empty string."""
    if inline and inline.strip():
        return inline.strip()
    if url:
        return await extract_text_from_url(url)
    return ""


@app.post("/api/ingest", response_model=IngestResponse)
async def ingest(request: IngestRequest):
    """Preprocess + chunk + embed an uploaded exam / answer key and add it to
    the live Chroma collection so future submissions can retrieve against it."""
    try:
        print("\n--- NEW INGEST REQUEST ---")
        questions_text = await _resolve_text(request.questions_text, request.questions_url)
        answer_key_text = await _resolve_text(request.answer_key_text, request.answer_key_url)

        if not questions_text and not answer_key_text:
            raise HTTPException(
                status_code=400,
                detail="Provide questions_text/questions_url and/or answer_key_text/answer_key_url.",
            )

        meta = ChunkMeta(
            course_code=request.course_code,
            year=request.year,
            semester=request.semester,
            moed=request.moed,
            version=request.version,
        )
        source_file = f"{meta.doc_id}__upload"
        chunks = build_chunks(
            meta, source_file,
            questions_text=questions_text or None,
            answer_key_text=answer_key_text or None,
        )

        documents, ids = [], []
        for c in chunks:
            metadata = {k: c.get(k, "") for k in _INDEXED_META_FIELDS}
            documents.append(Document(page_content=c["chunk_text"], metadata=metadata))
            ids.append(c["chunk_id"])

        # ids make the add idempotent: re-uploading the same exam upserts the
        # same chunk_ids instead of creating duplicates.
        vectorstore.add_documents(documents=documents, ids=ids)

        mode = ("paired" if questions_text and answer_key_text
                else "answer_key_only" if answer_key_text else "questions_only")
        type_counts = Counter(c["chunk_type"] for c in chunks)
        print(f"   -> Added {len(documents)} chunks ({dict(type_counts)}) as {meta.doc_id}")

        return IngestResponse(
            doc_id=meta.doc_id,
            chunks_added=len(documents),
            chunk_ids=ids,
            chunk_types=dict(type_counts),
            mode=mode,
        )

    except HTTPException:
        raise
    except Exception as e:
        print(f"ERROR: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))
