from math import isfinite, sqrt
from statistics import mean, median, pstdev
from time import perf_counter
from typing import List

from fastapi import FastAPI
from reverse_analysis import inspect_rounds
from pydantic import BaseModel, Field, field_validator

app = FastAPI(title="CrashAI Neo API", version="0.4.0")


class RoundsRequest(BaseModel):
    rounds: List[float] = Field(min_length=1, max_length=500)

    @field_validator("rounds")
    @classmethod
    def validate_rounds(cls, values):
        for value in values:
            if not isfinite(value) or value < 1:
                raise ValueError("Crash multipliers must be finite and >= 1x.")
        return values


class ScreenReadingsRequest(BaseModel):
    readings: List[str] = Field(min_length=1, max_length=500)


def parse_multiplier(text):
    # OCR can confuse decimal commas, spaces, and the multiplication sign.
    import re
    normalized = text.strip().lower().replace(",", ".").replace("×", "x")
    normalized = normalized.replace(" ", "")
    match = re.fullmatch(r"([0-9]{1,7}(?:\.[0-9]{1,4})?)x?", normalized)
    if not match:
        return None
    value = float(match.group(1))
    return value if isfinite(value) and value >= 1 else None


def summarize(rounds):
    start = perf_counter()
    count = len(rounds)
    ordered = sorted(rounds)
    average = mean(rounds)
    deviation = pstdev(rounds)
    def threshold(value):
        hits = sum(x >= value for x in rounds)
        probability = hits / count
        # Wilson 95% confidence interval; descriptive, not next-round certainty.
        z = 1.96
        denom = 1 + z*z/count
        center = (probability + z*z/(2*count))/denom
        half = z*sqrt(probability*(1-probability)/count + z*z/(4*count*count))/denom
        return {
            "observed_percent": round(probability*100, 2),
            "confidence_interval_95_percent": [
                round(max(0, center-half)*100, 2),
                round(min(1, center+half)*100, 2),
            ],
        }
    recent = rounds[-min(count, 20):]
    return {
        "status": "analysis_only",
        "count": count,
        "mean": round(average, 3),
        "median": round(median(ordered), 3),
        "min": round(ordered[0], 3),
        "max": round(ordered[-1], 3),
        "standard_deviation": round(deviation, 3),
        "recent_20_mean": round(mean(recent), 3),
        "recent_20_median": round(median(recent), 3),
        "percent_at_least_2x": threshold(2)["observed_percent"],
        "percent_at_least_5x": threshold(5)["observed_percent"],
        "percent_at_least_10x": threshold(10)["observed_percent"],
        "threshold_statistics": {
            "2x": threshold(2), "5x": threshold(5), "10x": threshold(10)
        },
        "prediction_available": False,
        "processing_ms": round((perf_counter()-start)*1000, 2),
        "message": "Historical estimates only; no guaranteed next-round prediction."
    }


@app.get("/")
def root():
    return {"app": "CrashAI Neo API", "status": "ok", "prediction_available": False}


@app.get("/health")
def health():
    return {"status": "ok", "version": "0.4.0"}


@app.post("/analyze")
def analyze(payload: RoundsRequest):
    return summarize(payload.rounds)


@app.post("/analyze-screen-readings")
def analyze_screen_readings(payload: ScreenReadingsRequest):
    # Do not deduplicate by value: consecutive rounds can legitimately end alike.
    # OCR snapshots must be grouped by round ID on the Android client.
    clean = []
    rejected = []
    for index, reading in enumerate(payload.readings):
        parsed = parse_multiplier(reading)
        if parsed is None:
            rejected.append(index)
        else:
            clean.append(parsed)
    return {
        "accepted_count": len(clean),
        "rejected_count": len(rejected),
        "rejected_indices": rejected,
        "cleaned_rounds": clean,
        "analysis": summarize(clean) if clean else None,
        "note": "Send only one final multiplier per completed round."
    }


@app.post("/reverse-analysis")
def reverse_analysis(payload: RoundsRequest):
    return inspect_rounds(payload.rounds)
