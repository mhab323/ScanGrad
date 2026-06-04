import re
from langchain_core.prompts import PromptTemplate
from langchain_google_genai import ChatGoogleGenerativeAI  # noqa: F401 (kept as a test patch seam)
from langchain_core.load import dumps, loads
from langchain_core.runnables import RunnableLambda
from models import EvaluationResponse
from typing import cast, Optional
from langchain_ollama import ChatOllama

# Strips list enumeration the query LLM tends to prepend ("1. ", "- ", "* ").
_ENUM_RE = re.compile(r"^\s*(?:\d+[.)]|[-*•])\s*")


class RAGGenerator:
    # Only these chunk types carry rubric / answer-key / question signal. We bias
    # retrieval toward them so the grader sees rubrics, not raw reading passages
    # (the corpus is ~20% instruction/reading noise).
    RUBRIC_CHUNK_TYPES = ["answer_key", "question"]

    def __init__(self, vectorstore):
        # Keep the store (not a fixed retriever) so each evaluation can build a
        # retriever with a request-specific metadata filter.
        self.vectorstore = vectorstore
        # Lightweight model just for generating retrieval queries.
        self.query_llm = ChatOllama(model="llama3.2:3b", temperature=0)
        # Stronger local model for the actual grading + structured output.
        self.grading_llm = ChatOllama(model="llama3.1:8b", temperature=0)

        self.query_prompt = PromptTemplate(
            input_variables=["exam_question"],
            template="""You are an AI assistant for an exam grading system.
            Generate 3 different search queries based on the following exam question to find similar past questions, rubrics, and grading examples in our database.
            Original Question: {exam_question}"""
        )

        self.answer_prompt = PromptTemplate.from_template(
            """You are a strict and consistent exam evaluator.
            You must grade the new Student Submission below.

            To ensure consistency, look at the Context (which contains similar past questions, rubrics, and examples of
            how they were graded).
            You MUST match the grading logic, strictness, and explanation style of the past examples.

            Context (Past Examples & Rubrics):
            {context}

            Current Exam Question:
            {exam_question}

            Student Submission:
            {student_submission}

            INSTRUCTIONS:
            1. Each question has its point value printed next to it in the exam (e.g. "(15 points)", "[20 pts]", "/25", "(5)"). For every question, set max_score to that printed point value and grade score out of that same maximum. If a question shows no point value, default max_score to 10. A question's score must be between 0 and its max_score and can NEVER exceed max_score.
            2. For every QuestionEvaluation, copy the exact wording of the question into question_text.
            3. For every QuestionEvaluation, transcribe the student's answer verbatim into student_answer (use an empty string if the student left it blank). Do NOT include the correct/model answer anywhere.
            4. Calculate the overall score as (sum of all question scores / sum of all max_scores) * 100.
            5. Write the explanation exactly how the past examples were written.
            6. Set confidence_level to 'HIGH' if the Context contains relevant rubrics, 'LOW' otherwise.
            """
        )

    @staticmethod
    def get_unique_union(documents: list[list]):
        # Order-preserving dedup: keep first occurrence so the context fed to
        # the LLM is identical run-to-run (set() ordering is not stable).
        seen = set()
        unique_docs = []
        for sublist in documents:
            for doc in sublist:
                key = dumps(doc)
                if key not in seen:
                    seen.add(key)
                    unique_docs.append(loads(key))
        return unique_docs

    @staticmethod
    def format_docs(docs):
        return "\n\n".join(doc.page_content for doc in docs)

    def build_filter(self, course_code: Optional[str] = None) -> dict:
        """Chroma `where` filter: rubric/question chunks, optionally scoped to the
        course. 'unknown' is included because the original answer-key corpus was
        indexed without a course_code — excluding it would drop the very rubrics
        we want to retrieve."""
        clauses: list[dict] = [{"chunk_type": {"$in": self.RUBRIC_CHUNK_TYPES}}]
        if course_code:
            clauses.append({"course_code": {"$in": [course_code, "unknown"]}})
        return clauses[0] if len(clauses) == 1 else {"$and": clauses}

    def _make_retriever(self, filter_dict: Optional[dict]):
        search_kwargs: dict = {"k": 10}
        if filter_dict:
            search_kwargs["filter"] = filter_dict
        return self.vectorstore.as_retriever(search_kwargs=search_kwargs)

    def _generate_queries(self, exam_question: str) -> list[str]:
        """Multi-query expansion. The original question is always included so
        retrieval is anchored even if the LLM drifts."""
        msg = (self.query_prompt | self.query_llm).invoke({"exam_question": exam_question})
        expanded = [_ENUM_RE.sub("", line).strip() for line in msg.content.split("\n")]
        expanded = [q for q in expanded if q]
        # De-dup while preserving order, original question first.
        ordered = [exam_question, *expanded]
        seen, out = set(), []
        for q in ordered:
            if q not in seen:
                seen.add(q)
                out.append(q)
        return out

    def _retrieve(self, queries: list[str], filter_dict: Optional[dict]):
        retriever = self._make_retriever(filter_dict)
        docs_per_query = retriever.map().invoke(queries)
        return self.get_unique_union(docs_per_query)

    def _retrieve_context(self, exam_question: str, course_code: Optional[str]) -> str:
        """Metadata-filtered retrieval, then semantic ranking within the filter.
        Falls back to unfiltered retrieval if the filter yields nothing so we
        never hand the grader an empty context."""
        queries = self._generate_queries(exam_question)
        docs = self._retrieve(queries, self.build_filter(course_code))
        if not docs:
            print("   Metadata filter returned no chunks; falling back to unfiltered semantic retrieval.")
            docs = self._retrieve(queries, None)
        print(f"   Retrieved {len(docs)} context chunk(s) "
              f"(filter course_code={course_code or 'any'}, types={self.RUBRIC_CHUNK_TYPES}).")
        return self.format_docs(docs)

    def build_chain(self):
        structured_llm = self.grading_llm.with_structured_output(EvaluationResponse)

        rag_chain = (
                {
                    "context": RunnableLambda(
                        lambda x: self._retrieve_context(x["exam_question"], x.get("course_code"))
                    ),
                    "exam_question": lambda x: x["exam_question"],
                    "student_submission": lambda x: x["student_submission"],
                }
                | self.answer_prompt
                | structured_llm
        )
        return rag_chain

    def evaluate_submission(self, exam_question: str, student_submission: str,
                            course_code: Optional[str] = None) -> EvaluationResponse:
        chain = self.build_chain()
        print(f"\nSearching for past examples similar to: '{exam_question}' "
              f"(course_code={course_code or 'any'})...")

        result = cast(EvaluationResponse, chain.invoke({
            "exam_question": exam_question,
            "student_submission": student_submission,
            "course_code": course_code,
        }))

        # The grading LLM is unreliable at both the per-question bounds and the
        # (Σscore / Σmax) * 100 arithmetic, so normalize deterministically:
        #   - every max_score must be positive (fall back to 10 when the model
        #     failed to read a printed point value),
        #   - every score is clamped into [0, max_score]; the model sometimes
        #     awards more than the question is worth (e.g. 12/6), which would
        #     otherwise push the scaled overall past 100,
        #   - the scaled overall is recomputed from the clamped marks.
        for e in result.evaluations:
            if e.max_score <= 0:
                e.max_score = 10.0
            e.score = max(0.0, min(e.score, e.max_score))

        total_max = sum(e.max_score for e in result.evaluations)
        if total_max > 0:
            total_score = sum(e.score for e in result.evaluations)
            result.overall_score = round(total_score / total_max * 100, 1)
        return result
