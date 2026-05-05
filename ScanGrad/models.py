from pydantic import BaseModel


class EvaluationRequest(BaseModel):
    submission_id: str
    course_code: str
    extracted_text: str
    image_url: str


class EvaluationResponse(BaseModel):
    final_score: int
    feedback: str
    confidence_level: str
