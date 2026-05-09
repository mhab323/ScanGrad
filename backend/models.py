from pydantic import BaseModel, Field
from typing import List, Optional


class QuestionEvaluation(BaseModel):
    question_id: str = Field(description="The number or name of the question being graded, e.g. 'Q1'")
    question_text: str = Field(
        description="The exact text of the question as it appears in the student submission")
    score: float = Field(description="The score given for this question, strictly out of 10")
    max_score: float = Field(
        default=10.0,
        description="The maximum possible score for this question. Always 10 unless the rubric says otherwise")
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
