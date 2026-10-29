"""Local SQLite persistence and JSON export for confirmed round outcomes.

Render's default ephemeral filesystem may reset on redeploy; configure
CRASHAI_DB_PATH on a persistent disk for durable production retention.
"""
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
    connection = sqlite3.connect(str(path), timeout=5)
    connection.execute("""CREATE TABLE IF NOT EXISTS rounds (
        round_id TEXT PRIMARY KEY,
        multiplier REAL NOT NULL CHECK(multiplier >= 1),
        recorded_at TEXT NOT NULL
    )""")
    connection.execute("CREATE INDEX IF NOT EXISTS idx_rounds_recorded_at ON rounds(recorded_at)")
    return connection

def save_round(round_id, multiplier):
    with _LOCK:
        with _connect() as db:
            cursor = db.execute(
                "INSERT OR IGNORE INTO rounds (round_id, multiplier, recorded_at) VALUES (?, ?, ?)",
                (round_id, multiplier, datetime.now(timezone.utc).isoformat())
            )
            return cursor.rowcount == 1

def recent_rounds(limit=500):
    limit = max(1, min(int(limit), 500))
    with _LOCK:
        with _connect() as db:
            rows = db.execute(
                "SELECT round_id, multiplier, recorded_at FROM rounds "
                "ORDER BY recorded_at DESC, rowid DESC LIMIT ?", (limit,)
            ).fetchall()
    return [{"round_id": row[0], "multiplier": row[1], "recorded_at": row[2]}
            for row in reversed(rows)]
