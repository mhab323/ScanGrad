import json
import os
from langchain_core.documents import Document
from langchain_community.vectorstores import Chroma
from langchain_huggingface import HuggingFaceEmbeddings


class DocumentIndexer:
    def __init__(self, file_path: str):
        self.file_path = file_path

    def build_vectorstore(self):
        print(f"Loading pre-processed exam chunks from {self.file_path}...")

        docs = []

        with os.scandir(self.file_path) as entries:
            for entry in entries:
                if not (entry.is_file() and entry.name.endswith('.jsonl')):
                    continue

                with open(entry.path, 'r', encoding='utf-8') as file:
                    for line in file:
                        if not line.strip():
                            continue

                        item = json.loads(line)

                        chunk_text = item.get('chunk_text', '').strip()

                        if not chunk_text:
                            continue

                        custom_metadata = {
                            "chunk_id": item.get("chunk_id", ""),
                            "course_code": item.get("course_code", ""),
                            "year": item.get("year", ""),
                            "semester": item.get("semester", ""),
                            "moed": item.get("moed", ""),
                            "chunk_type": item.get("chunk_type", ""),
                            "page_start": item.get("page_start", 0),
                            "source_file": entry.name
                        }

                        doc = Document(
                            page_content=chunk_text,
                            metadata=custom_metadata
                        )
                        docs.append(doc)

        if not docs:
            raise ValueError("No chunks were loaded. Check your JSONL file format.")

        print(f"Loaded {len(docs)} clean chunks. Embedding and storing in ChromaDB...")

        embedding_model = HuggingFaceEmbeddings(
            model_name="sentence-transformers/all-MiniLM-L6-v2",
            model_kwargs={"device": "cpu"},
            encode_kwargs={"normalize_embeddings": True},
        )
        vectorstore = Chroma.from_documents(
            documents=docs,
            embedding=embedding_model,
            persist_directory="./chroma_db",
        )

        print("Database successfully built!")
        return vectorstore
