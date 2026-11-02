# Zakaria Crash Monitoring — adapted collector

Source project: https://github.com/Zakaria-laktati/crash-1xbet-monitoring

The source project's MIT license notice is retained in this folder. Review the
upstream attribution before redistribution.

## Live collector → PostgreSQL → CrashAI Neo

`run_scraper.py` listens for completed `OnCrash` events and stores their
round ID, final multiplier, and timestamp in the `crash_games` PostgreSQL
table. It reads all account/session credentials from process environment
variables; no credential values belong in this repository, dashboard, APK,
or chat.

Required collector environment:
- `DATABASE_URL`: PostgreSQL URL for the monitoring database.
- `ZAKARIA_USER_TOKEN`
- `ZAKARIA_SESSION`
- `ZAKARIA_ACCESS_TOKEN`
- `ZAKARIA_ACCOUNT_ID`

Optional WebSocket parameters are documented in `.env.example`. The
`.env.example` file contains blanks only. Keep any populated local env file
private and untracked.

Install dependencies from this directory:
```sh
python -m pip install -r requirements.txt
```

Initialize the monitoring database with `database/init.sql` using a PostgreSQL
database/user that you control, then start the collector in an environment
where the required variables are set:
```sh
python run_scraper.py
```

In a separate process, configure the relay:
- `DATABASE_URL`: same monitoring database.
- `CRASHAI_API_URL`: `https://crashai-neo-api.onrender.com`
- `CRASHAI_INGEST_KEY`: the server-side ingestion secret already configured
  for CrashAI Neo.
- Optional: `RELAY_INTERVAL_SECONDS` (default 5).

Then run:
```sh
python relay_to_crashai.py
```

The relay forwards completed database rows only to
`POST /external-feed/import`; it does not forecast future outcomes. The
collector and relay have not yet been run against a live source or database,
and this branch has not been deployed. A successful API health response alone
does not prove that the data path is connected.
