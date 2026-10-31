"""Privacy-preserving journal of signal metadata, never raw traffic payloads."""
import os
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from threading import Lock

_DB = os.environ.get("CRASHAI_DB_PATH", "/tmp/crashai_rounds.sqlite3")
_LOCK = Lock()


def _connect():
    path = Path(_DB)
    path.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(str(path), timeout=5)
    db.execute("""CREATE TABLE IF NOT EXISTS signal_events (
        id INTEGER PRIMARY KEY AUTOINCREMENT, event_type TEXT NOT NULL,
        source_host TEXT NOT NULL, round_id TEXT NOT NULL, observed_at TEXT NOT NULL,
        phase TEXT NOT NULL, payload_keys TEXT NOT NULL, recorded_at TEXT NOT NULL
    )""")
    db.execute("CREATE INDEX IF NOT EXISTS idx_signal_round ON signal_events(round_id)")
    return db


def record_signal_event(event_type, source_host, round_id, observed_at, phase, payload_keys):
    try:
        stamp = datetime.fromisoformat(observed_at.replace("Z", "+00:00"))
        if stamp.tzinfo is None:
            raise ValueError("timezone required")
        observed = stamp.astimezone(timezone.utc).isoformat()
    except ValueError as exc:
        raise ValueError("observed_at must be an offset-aware ISO-8601 timestamp") from exc
    keys = sorted(set(str(k)[:64] for k in payload_keys))
    with _LOCK:
        with _connect() as db:
            db.execute(
                "INSERT INTO signal_events(event_type,source_host,round_id,observed_at,phase,payload_keys,recorded_at) VALUES(?,?,?,?,?,?,?)",
                (event_type, source_host[:253], round_id[:128], observed, phase[:24],
                 ",".join(keys), datetime.now(timezone.utc).isoformat()))
            event_id = db.execute("SELECT last_insert_rowid()").fetchone()[0]
    return {"status": "recorded", "event_id": event_id,
            "stored_fields": ["event_type", "source_host", "round_id", "observed_at", "phase", "payload_keys"],
            "raw_payload_stored": False}


def signal_trace_status(limit=100):
    with _LOCK:
        with _connect() as db:
            total = db.execute("SELECT COUNT(*) FROM signal_events").fetchone()[0]
            rows = db.execute("SELECT event_type,source_host,round_id,observed_at,phase,payload_keys FROM signal_events ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
    counts = {}
    for row in rows:
        counts[row[0]] = counts.get(row[0], 0) + 1
    return {"event_count": total, "recent_event_count": len(rows),
            "events_by_type": counts,
            "recent": [{"event_type": r[0], "source_host": r[1], "round_id": r[2],
                        "observed_at": r[3], "phase": r[4],
                        "payload_keys": [k for k in r[5].split(",") if k]} for r in rows],
            "analysis": "Metadata timeline only; source attribution requires independently captured evidence."}
