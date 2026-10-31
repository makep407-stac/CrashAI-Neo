from math import isfinite, sqrt
import os
import hmac
import hashlib
from statistics import mean, median, pstdev
from time import perf_counter
from typing import List

from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from reverse_analysis import inspect_rounds
from online_learning import learner
from advanced_statistics import analyze_three_ways
from round_storage import save_round, recent_rounds
from forecast_tracking import prepare_forecast, record_outcome, forecast_status
from screen_reading import validate_completed_round
from pattern_diagnostics import inspect_structure
from neural_engine import select_single_estimate
from intelligence_engine import analyze_intelligence
from signal_trace import record_signal_event, signal_trace_status
from pydantic import BaseModel, Field, field_validator

app = FastAPI(title="CrashAI Neo API", version="0.8.0")


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
    return {"status": "ok", "version": "0.8.0"}


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
        # Always persist: the in-memory learner can outlive/precede SQLite writes.
        saved = save_round(payload.round_id, payload.final_multiplier)
        result["saved_to_sqlite"] = saved
        if saved:
            result["forecast_evaluation"] = record_outcome(payload.round_id, payload.final_multiplier)
        result["stored_round_count"] = len(recent_rounds(500))
        return result
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.get("/learning-status")
def learning_status():
    return learner.status()


@app.get("/intelligence")
def intelligence(limit: int = 500):
    """Full adaptive ensemble report, evaluated strictly in chronological order."""
    if not 10 <= limit <= 500:
        raise HTTPException(status_code=422, detail="limit must be 10..500")
    rounds = [row["multiplier"] for row in recent_rounds(limit)]
    return analyze_intelligence(rounds)


@app.post("/intelligence/analyze")
def intelligence_analyze(payload: RoundsRequest):
    """Analyze a supplied chronological sequence without storing or training on it."""
    return analyze_intelligence(payload.rounds)


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



def responsive_historical_estimate(rounds):
    """Single-number estimate selected only by chronological walk-forward tests."""
    return select_single_estimate(rounds)["estimate_multiplier"]


@app.get("/next-estimate")
def next_estimate(limit: int = 200):
    """Descriptive robust point estimate; NOT a validated prediction of the next Crash outcome."""
    if not 1 <= limit <= 500:
        raise HTTPException(status_code=422, detail="limit must be 1..500")
    rounds = [row["multiplier"] for row in recent_rounds(limit)]
    if not rounds:
        return {"available": True, "estimate_multiplier": 2.0, "sample_count": 0,
                "required_rounds": 0, "method": "uncalibrated_reference_2x",
                "validated_next_round_prediction": False,
                "note": "2x is an uncalibrated reference, not a prediction; no recorded rounds yet."}
    # Median is resistant to the extremely long upper tail of Crash multipliers.
    # Avoid an apparent high-precision prediction: two decimals are display precision only.
    baseline = median(rounds)
    recent = median(rounds[-min(50, len(rounds)):])
    neural = select_single_estimate(rounds)
    estimate = neural["estimate_multiplier"]
    ordered = sorted(rounds)
    def percentile(q):
        index = (len(ordered) - 1) * q
        lo, hi = int(index), min(int(index) + 1, len(ordered) - 1)
        return round(ordered[lo] + (ordered[hi] - ordered[lo]) * (index - lo), 2)
    thresholds = {str(t): round(100 * sum(v >= t for v in rounds) / len(rounds), 1) for t in (1.5, 2, 3, 5, 10)}
    return {"available": True, "estimate_multiplier": estimate,
            "historical_range_p25_p75": [percentile(0.25), percentile(0.75)],
            "historical_threshold_percentages": thresholds,
            "recent_20_median": round(median(rounds[-20:]), 2),
            "sample_count": len(rounds), "method": neural["selected_model"],
            "neural_engine": neural,
            "validated_next_round_prediction": neural["validated_predictive_edge"],
            "note": "Single-number historical estimate. An edge is reported only when chronological tests beat the median baseline; no outcome is guaranteed."}


class PrepareRoundRequest(BaseModel):
    round_id: str = Field(min_length=1, max_length=128)


@app.post("/prepare-round")
def prepare_round(payload: PrepareRoundRequest):
    """Store one pre-round historical estimate under the upcoming round ID."""
    rounds = [row["multiplier"] for row in recent_rounds(200)]
    if not rounds:
        return prepare_forecast(payload.round_id, 2.0, 0)
    neural = select_single_estimate(rounds)
    result = prepare_forecast(payload.round_id, neural["estimate_multiplier"], len(rounds))
    result["neural_engine"] = neural
    return result


@app.get("/forecast-status")
def get_forecast_status():
    return forecast_status()


@app.get("/pre-result-audit")
def pre_result_audit():
    """Evidence gate: do not mistake estimates or late-arriving results for advance knowledge."""
    import sqlite3
    from forecast_tracking import _DB_PATH
    from datetime import datetime
    with sqlite3.connect(_DB_PATH) as db:
        rows = db.execute(
            "SELECT estimate, actual, created_at, evaluated_at "
            "FROM forecasts WHERE actual IS NOT NULL ORDER BY created_at DESC LIMIT 500"
        ).fetchall()
    eligible = []
    for estimate, actual, created, evaluated in rows:
        try:
            lead = (datetime.fromisoformat(evaluated) - datetime.fromisoformat(created)).total_seconds()
        except (ValueError, TypeError):
            continue
        if lead > 0:
            eligible.append((float(estimate), float(actual), lead))
    exact = sum(abs(pred - actual) < 0.005 for pred, actual, _ in eligible)
    return {
        "status": "not_verified",
        "evaluated_pairs": len(eligible),
        "exact_matches": exact,
        "exact_match_rate_percent": round(100 * exact / len(eligible), 2) if eligible else None,
        "median_record_to_evaluation_seconds": round(median([lead for _, _, lead in eligible]), 2) if eligible else None,
        "advance_result_proven": False,
        "warning": "Evaluation time is not actual crash time. Existing forecasts are generated after the first visible multiplier, so timestamps do not establish pre-round knowledge. A trusted server-side round timeline and independently captured outcome are required.",
    }


@app.get("/pattern-diagnostics")
def pattern_diagnostics(limit: int = 500):
    return inspect_structure([row["multiplier"] for row in recent_rounds(limit)])


@app.websocket("/ws/status")
async def ws_status(websocket: WebSocket):
    """Public read-only status stream; no credentials or account session exposed."""
    import asyncio
    await websocket.accept()
    try:
        while True:
            rows = recent_rounds(500)
            await websocket.send_json({
                "type": "status",
                "sample_count": len(rows),
                "required_rounds": 20,
                "estimate": next_estimate(200),
            })
            await asyncio.sleep(5)
    except (WebSocketDisconnect, RuntimeError):
        pass


class SignalEventRequest(BaseModel):
    event_type: str = Field(pattern="^(round_visible|countdown_seen|multiplier_visible|result_label_seen|manual_network_event)$")
    source_host: str = Field(default="screen-ocr", max_length=253)
    round_id: str = Field(default="", max_length=128)
    observed_at: str = Field(min_length=10, max_length=40)
    phase: str = Field(default="unknown", max_length=24)
    payload_keys: List[str] = Field(default_factory=list, max_length=40)


@app.post("/signal-trace/event")
def signal_trace_event(payload: SignalEventRequest):
    forbidden = ("authorization", "cookie", "token", "password", "session", "secret")
    keys = [key.strip().lower() for key in payload.payload_keys]
    if any(any(word in key for word in forbidden) for key in keys):
        raise HTTPException(status_code=400, detail="Sensitive payload keys are not accepted.")
    host = payload.source_host.strip().lower()
    if not host or any(ch in host for ch in "/:@?#"):
        raise HTTPException(status_code=422, detail="source_host must be a hostname only.")
    try:
        return record_signal_event(payload.event_type, host, payload.round_id.strip(),
                                   payload.observed_at, payload.phase, keys)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.get("/signal-trace/status")
def get_signal_trace_status(limit: int = 100):
    if not 1 <= limit <= 500:
        raise HTTPException(status_code=422, detail="limit must be 1..500")
    return signal_trace_status(limit)
