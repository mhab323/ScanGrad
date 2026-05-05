from embedder import embed
from rag import add_rule

COURSE_CODE = "ENG-ADV-B"

rules = [
    # TODO: Add rubric entries derived from past exams.
]

for rule in rules:
    embedding = embed(rule["text"])
    add_rule(
        rule_id=rule["id"],
        rule_text=rule["text"],
        embedding=embedding,
        course_code=rule["course_code"]
    )
    print(f"Ingested: {rule['id']}")

print("Done.")
