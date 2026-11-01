# External round-feed bridge

This integration branch adds a small, isolated API adapter to CrashAI Neo.
It accepts **normalized completed-round outcomes** from an upstream collector
that the operator is authorized to use. It does not connect to 1xBet, perform
account login, extract browser cookies, or obtain session/access tokens.

## Configure the server

Set a random secret of at least 32 characters as the Render environment variable:

- `CRASHAI_INGEST_KEY`

Keep this value server-side. Do not put it in the Android APK, GitHub, logs,
or chat messages. If it is missing or shorter than 32 characters, ingestion
returns HTTP 503.

## Endpoint

`POST /external-feed/import`

Header:

`X-CrashAI-Ingest-Key: <server-side secret>`

JSON body:

```json
{
  "source": "authorized_external_feed",
  "rounds": [
    {"round_id": "sample-round-001", "multiplier": 1.42},
    {"round_id": "sample-round-002", "multiplier": 3.18}
  ]
}
```

Only finite completed multipliers >= 1 are accepted. A batch can contain at
most 100 rounds. Duplicate round IDs are ignored by the existing SQLite store.

The response reports accepted and duplicate counts. This adapter does not
create forecasts and does not claim advance knowledge of any outcome.

## Validation checklist

1. Run the existing server test suite and an application startup check.
2. With no `CRASHAI_INGEST_KEY`, verify the endpoint returns 503.
3. With a wrong key, verify it returns 401.
4. With a valid test key and synthetic rounds, verify one insert and duplicate handling.
5. Confirm existing Android endpoints and the Render deployment remain unchanged
   until a separate reviewed deployment is approved.

## Scope boundary

The upstream collector must use an officially authorized data interface or
operator-provided feed. Do not submit account passwords, cookies, session IDs,
access tokens, or other browser credentials through this endpoint.
