from contextlib import asynccontextmanager

from config import load_environment, lifespan

load_environment()

from fastapi import FastAPI, HTTPException

from models import EvaluationRequest, EvaluationResponse
from ocr import extract_text_from_url
from generator import RAGGenerator
from langchain_chroma import Chroma
from langchain_huggingface import HuggingFaceEmbeddings
import socket
import firebase_admin
from firebase_admin import credentials, firestore
import time

print("Initializing Vector Database and RAG Generator...")
embedding_model = HuggingFaceEmbeddings(model_name="all-MiniLM-L6-v2")
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
            student_submission=submission_text
        )
        print(f"   -> AI Evaluation Finished in {round(time.time() - rag_start, 2)} seconds!")

        print(f"--- TOTAL TIME: {round(time.time() - start_time, 2)} seconds ---\n")
        return final_evaluation

    except Exception as e:
        print(f"ERROR: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))
