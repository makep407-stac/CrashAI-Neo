# Zakaria Crash Monitoring — reference snapshot

Source project: https://github.com/Zakaria-laktati/crash-1xbet-monitoring

Original project license: MIT; attribution and license notice are retained in this folder.

This directory is reserved for selected dashboard, database, logging, and dependency files adapted from the source project. Credential extraction utilities, browser-session capture instructions, and hard-coded credentials are excluded.

This snapshot is not a live collector and does not establish a connection to any betting platform. Integration with CrashAI Neo's authenticated completed-round import endpoint and tests remain pending.

## CrashAI Neo integration

The relay in `relay_to_crashai.py` reads the latest completed rows from the
`crash_games` PostgreSQL table and submits them to CrashAI Neo's
`POST /external-feed/import` endpoint. The API deduplicates rows by round ID.

Configure these variables in the relay process environment (never commit values):
- `DATABASE_URL`: PostgreSQL connection URL for the monitoring database.
- `CRASHAI_API_URL`: CrashAI Neo API base URL.
- `CRASHAI_INGEST_KEY`: same secret as the API's server-side setting.
- Optional: `RELAY_INTERVAL_SECONDS` (default 5).

Install the source project's requirements, then run:
```sh
python relay_to_crashai.py
```

The relay forwards completed database rows only. It does not start or replace
the upstream collector. A separate, functioning producer must populate
`crash_games`. Do not configure credentials in source files or the dashboard.
The dashboard now contains display controls only and requires explicit database
environment configuration. No browser token capture is provided.

This repository branch has not been deployed or connected to a live data source.
