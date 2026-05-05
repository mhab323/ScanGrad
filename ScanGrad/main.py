from fastapi import FastAPI, HTTPException
from models import EvaluationRequest, EvaluationResponse

app = FastAPI()


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/api/evaluate", response_model=EvaluationResponse)
async def evaluate(request: EvaluationRequest):
    mock_id = request.submission_id.lower()

    if mock_id == "good":
        return EvaluationResponse(
            final_score=96,
            feedback="Excellent work on the 'A Sea of Sensors' exam. You correctly identified that software analysis may lead to smart physical infrastructures. Your justifications for the TRUE/FALSE section were spot on.",
            confidence_level="HIGH"
        )
    elif mock_id == "mid":
        return EvaluationResponse(
            final_score=72,
            feedback="Solid effort. You successfully answered the TRUE/FALSE section regarding digital cameras and Twitter, but missed the mark on the open-ended questions about human data generation. Review paragraph E for those answers.",
            confidence_level="HIGH"
        )
    else:
        # Default to the "Bad" exam
        return EvaluationResponse(
            final_score=45,
            feedback="Incomplete submission. While you correctly answered the first two questions, the rest of the pages were either left blank or the handwriting was entirely illegible. The OCR failed to detect answers for Part Three.",
            confidence_level="LOW"
        )