"""Relay only newly completed rounds from PostgreSQL to CrashAI Neo."""
import logging
import os
import time
import psycopg2
import requests

logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
log = logging.getLogger("zakaria-relay")

def required(name):
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value

def read_after(connection, cursor_key, limit=250):
    with connection.cursor() as cursor:
        if cursor_key is None:
            cursor.execute(
                """SELECT game_id, multiplier, timestamp
                   FROM crash_games WHERE multiplier >= 1
                   ORDER BY timestamp DESC, game_id DESC LIMIT %s""", (limit,))
            rows = list(reversed(cursor.fetchall()))
        else:
            last_ts, last_id = cursor_key
            cursor.execute(
                """SELECT game_id, multiplier, timestamp
                   FROM crash_games
                   WHERE multiplier >= 1 AND (timestamp, game_id) > (%s, %s)
                   ORDER BY timestamp ASC, game_id ASC LIMIT %s""",
                (last_ts, last_id, limit))
            rows = cursor.fetchall()
    return rows

def main():
    database_url = required("DATABASE_URL")
    endpoint = required("CRASHAI_API_URL").rstrip("/") + "/external-feed/import"
    ingest_key = required("CRASHAI_INGEST_KEY")
    interval = max(2, int(os.getenv("RELAY_INTERVAL_SECONDS", "5")))
    cursor_key = None
    with psycopg2.connect(database_url) as connection:
        while True:
            try:
                rows = read_after(connection, cursor_key)
                if rows:
                    batch = [{"round_id": str(gid), "multiplier": float(mult)}
                             for gid, mult, _ in rows]
                    response = requests.post(
                        endpoint,
                        json={"source": "zakaria_postgres", "rounds": batch},
                        headers={"X-CrashAI-Ingest-Key": ingest_key},
                        timeout=20)
                    response.raise_for_status()
                    result = response.json()
                    cursor_key = (rows[-1][2], rows[-1][0])
                    log.info("Forwarded batch=%s accepted=%s duplicates=%s",
                             len(rows), result.get("accepted"),
                             result.get("duplicates"))
            except requests.RequestException as exc:
                log.warning("CrashAI endpoint request failed: %s", type(exc).__name__)
            except psycopg2.Error as exc:
                log.warning("PostgreSQL read failed: %s", type(exc).__name__)
                raise
            time.sleep(interval)

if __name__ == "__main__":
    main()
