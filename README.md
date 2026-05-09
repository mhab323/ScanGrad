# ScanGrad 🎓

An end-to-end, AI-powered grading platform for handwritten exams. ScanGrad streamlines the evaluation process by digitizing physical exams via an Android client and grading them using a custom Hybrid-LLM Retrieval-Augmented Generation (RAG) pipeline on a FastAPI backend.

## 📸 App Screenshots

<div align="center">
  <img src="screenshots/camera_view.png" width="220" alt="Dashboard"/>
  &nbsp;&nbsp;&nbsp;
  <img src="screenshots/validation_view.png" width="220" alt="Data Validation Screen"/>
  &nbsp;&nbsp;&nbsp;
  <img src="screenshots/results_view.png" width="220" alt="Grading Results Screen"/>
</div>


## 📑 Table of Contents
- [ScanGrad 🎓](#scangrad-)
  - [📸 App Screenshots](#-app-screenshots)
  - [📑 Table of Contents](#-table-of-contents)
  - [🚀 Overview](#-overview)
  - [🛠️ Tech Stack](#️-tech-stack)
  - [🧠 The AI Grading Pipeline (RAG)](#-the-ai-grading-pipeline-rag)
  - [📂 Repository Structure](#-repository-structure)

## 🚀 Overview
ScanGrad bridges the gap between physical examinations and automated digital grading. Students or professors can capture images of handwritten exam submissions using the Android client. The app extracts the text using OCR and sends it to the FastAPI backend, where a sophisticated LangChain RAG system evaluates the answer against a vector database of course-specific rubrics and past examples.

## 🛠️ Tech Stack

**Frontend (Android)**
* **Language:** Kotlin
* **Architecture:** MVVM (Model-View-ViewModel) with Coroutines & LiveData
* **Networking:** Retrofit & Gson
* **Hardware Interfacing:** CameraX

**Backend (Python)**
* **Framework:** FastAPI / Uvicorn
* **OCR Engine:** PyTesseract / pypdfium2

**AI & Data Pipeline**
* **Orchestration:** LangChain
* **Vector Database:** ChromaDB
* **Embeddings:** HuggingFace (`all-MiniLM-L6-v2`)
* **LLMs:** Hybrid Architecture utilizing **Llama 3** (via local Ollama) and **Gemini 2.5 Flash** (via Google AI Studio)

## 🧠 The AI Grading Pipeline (RAG)

ScanGrad utilizes an advanced **Multi-Query Retrieval** strategy to ensure accurate, rubric-aligned grading while efficiently managing API rate limits.

1. **OCR & Text Extraction:** The FastAPI server receives the image/PDF URL, downloads the file, and extracts the handwritten text.
2. **Multi-Query Generation (Llama 3):** To overcome the limitations of a single search query, the system feeds the original exam question to a local Llama 3 model. Llama generates three distinct, optimized search queries.
3. **Vector Retrieval (ChromaDB):** These queries search the Chroma vector database for relevant past examples, grading rubrics, and penalty rules. The system takes the unique union of all retrieved documents to form a comprehensive context window.
4. **Structured Evaluation (Gemini 2.5 Flash):** The student's submission, the exam question, and the retrieved context are passed to Gemini. Gemini acts as a strict evaluator, mapping its response precisely to a Pydantic `EvaluationResponse` model, ensuring the Android client receives perfectly formatted JSON.

## 📂 Repository Structure

This project uses a Monorepo architecture to keep the frontend and backend synchronized.

```text
ScanGrad/
├── android/               # Kotlin Android Studio Project
│   ├── app/               # UI, ViewModels, and Retrofit Network interfaces
│   └── build.gradle
├── backend/               # Python FastAPI & LangChain Project
│   ├── main.py            # API Endpoints
│   ├── generator.py       # Dual-LLM RAG Pipeline Engine
│   ├── ocr.py             # Image/PDF text extraction
│   └── models.py          # Pydantic data models
└── README.md
