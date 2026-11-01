import pytest
from fastapi import HTTPException

from zakaria_bridge import ImportBatch, import_completed_rounds


def test_ingest_requires_server_secret(monkeypatch):
    monkeypatch.delenv("CRASHAI_INGEST_KEY", raising=False)
    payload = ImportBatch(rounds=[{"round_id": "demo-1", "multiplier": 1.5}])
    with pytest.raises(HTTPException) as exc:
        import_completed_rounds(payload, "anything")
    assert exc.value.status_code == 503


def test_ingest_rejects_wrong_secret(monkeypatch):
    monkeypatch.setenv("CRASHAI_INGEST_KEY", "x" * 40)
    payload = ImportBatch(rounds=[{"round_id": "demo-2", "multiplier": 2.0}])
    with pytest.raises(HTTPException) as exc:
        import_completed_rounds(payload, "wrong")
    assert exc.value.status_code == 401


def test_ingest_accepts_and_deduplicates_completed_rounds(monkeypatch):
    monkeypatch.setenv("CRASHAI_INGEST_KEY", "test-secret-" + "x" * 32)
    saved = set()

    def fake_save(round_id, multiplier):
        if round_id in saved:
            return False
        saved.add(round_id)
        return True

    monkeypatch.setattr("zakaria_bridge.save_round", fake_save)
    monkeypatch.setattr("zakaria_bridge.recent_rounds", lambda limit: list(saved))
    payload = ImportBatch(rounds=[
        {"round_id": "demo-3", "multiplier": 1.25},
        {"round_id": "demo-3", "multiplier": 1.25},
    ])
    result = import_completed_rounds(payload, "test-secret-" + "x" * 32)
    assert result["accepted"] == 1
    assert result["duplicates"] == 1
    assert result["prediction_created"] is False


def test_ingest_rejects_invalid_multiplier():
    with pytest.raises(ValueError):
        ImportBatch(rounds=[{"round_id": "demo-4", "multiplier": 0.5}])
