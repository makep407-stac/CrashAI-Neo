from fastapi.testclient import TestClient

import main


def test_health():
    client = TestClient(main.app)
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_analyze_is_descriptive_only():
    client = TestClient(main.app)
    response = client.post("/analyze", json={"rounds": [1.2, 2.0, 5.0]})
    assert response.status_code == 200
    body = response.json()
    assert body["count"] == 3
    assert body["prediction_available"] is False


def test_ingest_requires_configured_key(monkeypatch):
    monkeypatch.delenv("CRASH_INGEST_API_KEY", raising=False)
    client = TestClient(main.app)
    response = client.post(
        "/ingest/round",
        json={"event_id": "test-1", "multiplier": 2.0}
    )
    assert response.status_code == 503
