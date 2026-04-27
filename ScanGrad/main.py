from fastapi import FastAPI, HTTPException

from grader import grade_submission
from models import EvaluationRequest, EvaluationResponse

app = FastAPI()


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/api/evaluate", response_model=EvaluationResponse)
async def evaluate(request: EvaluationRequest):
    try:
        return await grade_submission(request)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
