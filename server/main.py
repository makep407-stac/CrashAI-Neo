from math import isfinite, sqrt
import os
import hmac
import hashlib
from statistics import mean, median, pstdev
from time import perf_counter
from typing import List

from fastapi import FastAPI, HTTPException
from reverse_analysis import inspect_rounds
from online_learning import learner
from advanced_statistics import analyze_three_ways
from round_storage import save_round, recent_rounds
from screen_reading import validate_completed_round
from pydantic import BaseModel, Field, field_validator

app = FastAPI(title="CrashAI Neo API", version="0.7.0")


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
    return {"status": "ok", "version": "0.7.0"}


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


class CompletedRound(BaseModel):
    round_id: str = Field(min_length=1, max_length=128)
    final_multiplier: float


@app.post("/learn-round")
def learn_round(payload: CompletedRound):
    try:
        result = learner.add(payload.round_id, payload.final_multiplier)
        if result["status"] == "learned":
            saved = save_round(payload.round_id, payload.final_multiplier)
            result["saved_to_sqlite"] = saved
        return result
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.get("/learning-status")
def learning_status():
    return learner.status()


@app.post("/advanced-analysis")
def advanced_analysis(payload: RoundsRequest):
    return analyze_three_ways(payload.rounds)


@app.get("/round-history")
def round_history(limit: int = 100):
    return {"rounds": recent_rounds(limit), "storage": "sqlite",
            "note": "Persistence across deployments requires a persistent Render disk."}


@app.get("/analyze-stored-rounds")
def analyze_stored_rounds(limit: int = 500):
    rounds = [row["multiplier"] for row in recent_rounds(limit)]
    return analyze_three_ways(rounds)


class ScreenFramesRequest(BaseModel):
    round_id: str = Field(min_length=1, max_length=128)
    phase: str
    frames: List[str] = Field(min_length=1, max_length=10)


@app.post("/validate-screen-frames")
def validate_screen_frames(payload: ScreenFramesRequest):
    return validate_completed_round(payload.round_id, payload.frames, payload.phase)


class LoginRequest(BaseModel):
    account_id: str = Field(min_length=1, max_length=64)
    app_code: str = Field(min_length=1, max_length=128)


@app.post("/login")
def login(payload: LoginRequest):
    # These are application access controls, NOT 1xBet account authentication.
    allowed_id = os.environ.get("CRASHAI_ALLOWED_ACCOUNT_ID", "")
    expected_hash = os.environ.get("CRASHAI_APP_CODE_SHA256", "")
    if not allowed_id or len(expected_hash) != 64:
        raise HTTPException(status_code=503, detail="Application login is not configured.")
    supplied_hash = hashlib.sha256(payload.app_code.encode("utf-8")).hexdigest()
    account_ok = hmac.compare_digest(payload.account_id.strip(), allowed_id)
    code_ok = hmac.compare_digest(supplied_hash, expected_hash.lower())
    if not (account_ok and code_ok):
        raise HTTPException(status_code=401, detail="Invalid account ID or app code.")
    return {"authorized": True, "provider_account_verified": False,
            "message": "Application access granted; no 1xBet account linkage."}
