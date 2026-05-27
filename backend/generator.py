from langchain_core.prompts import PromptTemplate
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.load import dumps, loads
from models import EvaluationResponse
from typing import cast
from langchain_ollama import ChatOllama


class RAGGenerator:
    def __init__(self, vectorstore):
        self.retriever = vectorstore.as_retriever(search_kwargs={"k": 10})
        self.query_llm = ChatOllama(model="llama3.2:3b")
        self.grading_llm = ChatGoogleGenerativeAI(
            model="gemini-2.5-flash",
            temperature=0,
            max_retries=5
        )

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
            1. Grade each question out of 10 points (set max_score = 10).
            2. For every QuestionEvaluation, copy the exact wording of the question into question_text.
            3. Calculate the overall score, scaled to 100.
            4. Write the explanation exactly how the past examples were written.
            5. Set confidence_level to 'HIGH' if the Context contains relevant rubrics, 'LOW' otherwise.
            """
        )

    @staticmethod
    def get_unique_union(documents: list[list]):
        flattened_docs = [dumps(doc) for sublist in documents for doc in sublist]
        unique_docs = list(set(flattened_docs))
        return [loads(doc) for doc in unique_docs]

    @staticmethod
    def format_docs(docs):
        return "\n\n".join(doc.page_content for doc in docs)

    def build_chain(self):
        structured_llm = self.query_llm.with_structured_output(EvaluationResponse)

        retrieval_chain = (
                self.query_prompt
                | self.query_llm
                | (lambda x: x.content.split("\n"))
                | self.retriever.map()
                | self.get_unique_union
        )

        rag_chain = (
                {
                    "context": (lambda x: x["exam_question"]) | retrieval_chain | self.format_docs,
                    "exam_question": lambda x: x["exam_question"],
                    "student_submission": lambda x: x["student_submission"]
                }
                | self.answer_prompt
                | structured_llm
        )
        return rag_chain

    def evaluate_submission(self, exam_question: str, student_submission: str) -> EvaluationResponse:
        chain = self.build_chain()
        print(f"\nSearching for past examples similar to: '{exam_question}'...")

        result = chain.invoke({
            "exam_question": exam_question,
            "student_submission": student_submission
        })
        return cast(EvaluationResponse, result)