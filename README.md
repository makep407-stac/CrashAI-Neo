# CrashAI Neo v2 — real-time analysis foundation

This branch adds an authenticated ingestion API and WebSocket broadcaster for round data.

## Run locally

```bash
cd server
python -m pip install -r requirements.txt
export CRASH_INGEST_API_KEY="replace-with-a-long-random-secret"
uvicorn main:app --host 0.0.0.0 --port 8000
```

## Endpoints

- `GET /health` — health check.
- `POST /analyze` — descriptive statistics for supplied historical multipliers.
- `POST /ingest/round` — accept one round from an authorized collector.
- `GET /rounds/latest?limit=50` — read recent in-memory events.
- `WS /ws/rounds` — authenticated live event stream; send an Authorization: Bearer header.

Example ingestion:

```bash
curl -X POST http://localhost:8000/ingest/round \
  -H "Authorization: Bearer $CRASH_INGEST_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"event_id":"demo-001","multiplier":2.45,"source":"authorized-feed"}'
```

## Important integration boundary

This is a transport and analysis foundation, not a provider-specific Zakaria/1xBet connector. No provider WebSocket URL, message schema, or session-authentication contract was present in the repository. Do not paste access tokens, user tokens, or session cookies into source code, GitHub, chat, or logs. Connect a provider only using its documented, explicitly authorized API/feed contract. The service does not log in to user accounts or scrape private endpoints.

Events are kept in memory (up to 5,000) and are lost on restart. Configure a persistent database before production use. Historical statistics are not reliable predictions of the next random crash round.
