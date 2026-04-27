import json
import re

from anthropic import Anthropic

from embedder import embed
from models import EvaluationRequest, EvaluationResponse
from ocr import extract_text_from_url
from rag import retrieve_rules

_anthropic = Anthropic()


async def grade_submission(request: EvaluationRequest) -> EvaluationResponse:
    submission_text = await extract_text_from_url(request.image_url)

    submission_vector = embed(submission_text)

    retrieved_rules = retrieve_rules(
        submission_embedding=submission_vector,
        course_code=request.course_code
    )

    prompt = f"""You are an expert exam grader for {request.course_code}.

Use the following grading rules retrieved for this course:
---
{retrieved_rules}
---

Now grade the following student submission:
---
{submission_text}
---

Respond ONLY with a JSON object in this exact format, no other text:
{{"final_score": <integer 0-100>, "feedback": "<2-3 sentence feedback for the student>", "confidence_level": "<HIGH|MEDIUM|LOW>"}}

Set confidence_level to LOW if the submission text is unclear or incomplete."""

    response = _anthropic.messages.create(
        model="claude-opus-4-6",
        max_tokens=512,
        messages=[{"role": "user", "content": prompt}]
    )

    raw = response.content[0].text
    data = json.loads(re.search(r'\{.*\}', raw, re.DOTALL).group())
    return EvaluationResponse(**data)
