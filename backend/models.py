from pydantic import BaseModel, Field
from typing import List, Optional


class QuestionEvaluation(BaseModel):
    question_id: str = Field(description="The number or name of the question being graded, e.g. 'Q1'")
    question_text: str = Field(
        description="The exact text of the question as it appears in the student submission")
    student_answer: str = Field(
        default="",
        description="The student's answer transcribed verbatim from the scanned submission, for display in the app. "
                    "Empty string if the student left the question blank")
    score: float = Field(description="The score awarded for this question, out of this question's max_score")
    max_score: float = Field(
        default=10.0,
        description="The maximum points for this question, taken from the point value printed next to it in the exam "
                    "(e.g. 15, 20, 25). Use 10 only if no point value is shown")
    explanation: str = Field(
        description="A strict, 1-2 sentence explanation of why this score was given based on the rubric")


class EvaluationResponse(BaseModel):
    overall_score: float = Field(description="The total overall score for the exam, scaled to 100")
    evaluations: List[QuestionEvaluation] = Field(
        description="A list containing the grades for every individual question found in the scan")
    general_feedback: str = Field(description="Overall feedback for the entire exam submission")
    confidence_level: str = Field(description="HIGH if rubrics were found in context, LOW if context was missing")


class EvaluationRequest(BaseModel):
    submission_id: str
    course_code: str
    exam_question: str
    extracted_text: str
    image_url: Optional[str] = None


class IngestRequest(BaseModel):
    # Reliable indexing metadata, supplied by the uploader (form fields).
    course_code: str
    year: str
    semester: str
    moed: str
    version: str = "v1"
    # Content for each document: provide inline text OR a URL to OCR.
    # At least one of the two documents (questions / answer key) is required.
    questions_text: Optional[str] = None
    questions_url: Optional[str] = None
    answer_key_text: Optional[str] = None
    answer_key_url: Optional[str] = None


class IngestResponse(BaseModel):
    doc_id: str
    chunks_added: int
    chunk_ids: List[str]
    chunk_types: dict[str, int]
    mode: str  # "paired" | "answer_key_only" | "questions_only"
