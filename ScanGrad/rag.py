import chromadb

_client = chromadb.PersistentClient(path="./chroma_db")
_collection = _client.get_or_create_collection("grading_rules")


def retrieve_rules(submission_embedding: list[float], course_code: str, top_k: int = 5) -> str:
    results = _collection.query(
        query_embeddings=[submission_embedding],
        n_results=top_k,
        where={"course_code": course_code}
    )

    if not results["documents"] or not results["documents"][0]:
        return "No specific grading rules found for this course."

    return "\n\n".join(results["documents"][0])


def add_rule(rule_id: str, rule_text: str, embedding: list[float], course_code: str):
    _collection.add(
        ids=[rule_id],
        documents=[rule_text],
        embeddings=[embedding],
        metadatas=[{"course_code": course_code}]
    )
