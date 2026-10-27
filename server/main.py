from fastapi import FastAPI
from pydantic import BaseModel, Field
from typing import List

app = FastAPI(title="CrashAI Neo API", version="0.1.0")

class RoundsRequest(BaseModel):
rounds: List[float] = Field(default_factory=list, max_length=500)

@app.get("/")
def root():
return {
"app": "CrashAI Neo API",
"status": "ok",
"prediction_available": False,
"message": "Server is running. Historical data is not configured yet."
}

@app.get("/health")
def health():
return {"status": "ok"}

@app.post("/analyze")
def analyze(payload: RoundsRequest):
if not payload.rounds:
return {
"status": "no_data",
"message": "Submit historical round multipliers first.",
"count": 0
}

clean = [x for x in payload.rounds if x > 0]

if not clean:
    return {
        "status": "invalid_data",
        "message": "Values must be positive.",
        "count": 0
    }

return {
    "status": "analysis_only",
    "count": len(clean),
    "mean": round(sum(clean) / len(clean), 3),
    "min": min(clean),
    "max": max(clean),
    "prediction_available": False,
    "message": "Statistical analysis only; no reliable next-round prediction is available."
}
