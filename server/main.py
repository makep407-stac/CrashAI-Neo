from math import isfinite
from statistics import median, pstdev
from typing import List

from fastapi import FastAPI
from pydantic import BaseModel, Field, field_validator


app = FastAPI(
    title="CrashAI Neo API",
    version="0.2.0"
)


class RoundsRequest(BaseModel):
    rounds: List[float] = Field(
        min_length=1,
        max_length=500
    )

    @field_validator("rounds")
    @classmethod
    def validate_rounds(cls, values):
        for value in values:
            if not isfinite(value) or value <= 0:
                raise ValueError(
                    "All round multipliers must be finite and positive."
                )
        return values


@app.get("/")
def root():
    return {
        "app": "CrashAI Neo API",
        "status": "ok",
        "prediction_available": False,
        "message": "Statistical analysis API is running."
    }


@app.get("/health")
def health():
    return {
        "status": "ok",
        "version": "0.2.0"
    }


@app.post("/analyze")
def analyze(payload: RoundsRequest):
    rounds = payload.rounds
    count = len(rounds)

    ordered = sorted(rounds)

    def percentage(condition):
        matches = sum(1 for value in rounds if condition(value))
        return round(matches / count * 100, 2)

    result = {
        "status": "analysis_only",
        "count": count,
        "mean": round(sum(rounds) / count, 3),
        "median": round(median(rounds), 3),
        "min": round(min(rounds), 3),
        "max": round(max(rounds), 3),
        "standard_deviation": round(pstdev(rounds), 3),
        "percent_at_least_2x": percentage(
            lambda x: x >= 2
        ),
        "percent_at_least_5x": percentage(
            lambda x: x >= 5
        ),
        "percent_at_least_10x": percentage(
            lambda x: x >= 10
        ),
        "prediction_available": False,
        "message": (
            "Historical descriptive statistics only. "
            "These results do not establish a reliable "
            "prediction of the next round."
        )
    }

    return result
