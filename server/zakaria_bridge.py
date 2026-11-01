"""Safe, source-neutral ingestion adapter for externally authorized Crash feeds.

This module does not log in to betting accounts or collect browser/session secrets.
Only normalized, already-completed round outcomes may be submitted by a trusted
upstream collector. Keep the ingest key in the server's secret environment.
"""
import hmac
import os
from math import isfinite
from typing import List

from fastapi import Header, HTTPException
from pydantic import BaseModel, Field, field_validator

from round_storage import save_round, recent_rounds


class ImportedRound(BaseModel):
    round_id: str = Field(min_length=1, max_length=128)
    multiplier: float

    @field_validator("multiplier")
    @classmethod
    def valid_multiplier(cls, value: float) -> float:
        if not isfinite(value) or value < 1:
            raise ValueError("multiplier must be finite and >= 1")
        return value


class ImportBatch(BaseModel):
    source: str = Field(default="authorized_external_feed", min_length=1, max_length=64)
    rounds: List[ImportedRound] = Field(min_length=1, max_length=100)


def import_completed_rounds(payload: ImportBatch, supplied_key: str | None):
    expected_key = os.environ.get("CRASHAI_INGEST_KEY", "")
    if len(expected_key) < 32:
        raise HTTPException(status_code=503, detail="External ingestion is not configured.")
    if not supplied_key or not hmac.compare_digest(supplied_key, expected_key):
        raise HTTPException(status_code=401, detail="Invalid ingestion key.")

    inserted = 0
    duplicates = 0
    for item in payload.rounds:
        if save_round(item.round_id, item.multiplier):
            inserted += 1
        else:
            duplicates += 1

    return {
        "accepted": inserted,
        "duplicates": duplicates,
        "stored_round_count": len(recent_rounds(500)),
        "source": payload.source,
        "prediction_created": False,
        "note": "Imported completed outcomes only; this endpoint does not predict future rounds.",
    }


def ingest_key_header(x_crashai_ingest_key: str | None = Header(default=None)):
    return x_crashai_ingest_key
