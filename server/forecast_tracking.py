"""Persist pre-round estimates and evaluate them against confirmed outcomes."""
import os
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from threading import Lock

_DB_PATH = os.environ.get("CRASHAI_DB_PATH", "/tmp/crashai_rounds.sqlite3")
_LOCK = Lock()


def _connect():
    path = Path(_DB_PATH)
    path.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(str(path), timeout=5)
    db.execute("""CREATE TABLE IF NOT EXISTS forecasts (
        round_id TEXT PRIMARY KEY,
        estimate REAL NOT NULL CHECK(estimate >= 1),
        sample_count INTEGER NOT NULL,
        created_at TEXT NOT NULL,
        actual REAL,
        absolute_error REAL,
        evaluated_at TEXT
    )""")
    return db


def prepare_forecast(round_id, estimate, sample_count):
    now = datetime.now(timezone.utc).isoformat()
    with _LOCK:
        with _connect() as db:
            db.execute(
                "INSERT OR IGNORE INTO forecasts "
                "(round_id, estimate, sample_count, created_at) VALUES (?, ?, ?, ?)",
                (round_id, estimate, sample_count, now),
            )
            row = db.execute(
                "SELECT estimate, sample_count, actual, absolute_error FROM forecasts WHERE round_id = ?",
                (round_id,),
            ).fetchone()
    return {
        "available": True,
        "round_id": round_id,
        "estimate_multiplier": row[0],
        "sample_count": row[1],
        "already_evaluated": row[2] is not None,
        "method": "robust_historical_median_blend",
        "validated_next_round_prediction": False,
    }


def record_outcome(round_id, actual):
    if actual < 1:
        return {"status": "invalid_outcome"}
    now = datetime.now(timezone.utc).isoformat()
    with _LOCK:
        with _connect() as db:
            row = db.execute(
                "SELECT estimate, actual FROM forecasts WHERE round_id = ?",
                (round_id,),
            ).fetchone()
            if row is None:
                return {"status": "no_forecast_for_round", "round_id": round_id}
            if row[1] is not None:
                return {"status": "already_evaluated", "round_id": round_id}
            error = abs(float(row[0]) - float(actual))
            db.execute(
                "UPDATE forecasts SET actual = ?, absolute_error = ?, evaluated_at = ? WHERE round_id = ?",
                (float(actual), error, now, round_id),
            )
    return {
        "status": "evaluated",
        "round_id": round_id,
        "estimate_multiplier": float(row[0]),
        "actual_multiplier": float(actual),
        "absolute_error": round(error, 3),
    }


def forecast_status():
    with _LOCK:
        with _connect() as db:
            total, evaluated, mae = db.execute(
                "SELECT COUNT(*), COUNT(actual), AVG(absolute_error) FROM forecasts"
            ).fetchone()
            recent = db.execute(
                "SELECT round_id, estimate, actual, absolute_error, created_at "
                "FROM forecasts ORDER BY created_at DESC LIMIT 20"
            ).fetchall()
    return {
        "forecast_count": total,
        "evaluated_count": evaluated,
        "mean_absolute_error": round(mae, 3) if mae is not None else None,
        "recent": [
            {"round_id": row[0], "estimate_multiplier": row[1],
             "actual_multiplier": row[2], "absolute_error": row[3],
             "created_at": row[4]}
            for row in recent
        ],
        "note": "Historical evaluation only; a lower error does not guarantee future accuracy.",
    }
