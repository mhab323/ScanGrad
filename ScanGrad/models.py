from pydantic import BaseModel


class EvaluationRequest(BaseModel):
    submission_id: str
    course_code: str
    extracted_text: str   # empty string for now — OCR handles it server-side
    image_url: str


class EvaluationResponse(BaseModel):
    final_score: int
    feedback: str
    confidence_level: str
