"""Relay completed rounds from the Zakaria PostgreSQL store to CrashAI Neo.

Required environment:
  DATABASE_URL             PostgreSQL connection URL for the monitoring database
  CRASHAI_API_URL           Base URL, e.g. https://crashai-neo-api.onrender.com
  CRASHAI_INGEST_KEY        Secret configured on the CrashAI Neo server

This process forwards completed rows only. It does not collect account credentials,
connect to a betting account, or predict future outcomes.
"""
import os
import time
import logging

import psycopg2
import requests

logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
log = logging.getLogger("zakaria-relay")


def required(name):
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def read_recent(connection, limit=100):
    with connection.cursor() as cursor:
        cursor.execute(
            """
            SELECT game_id, multiplier
            FROM crash_games
            WHERE multiplier >= 1
            ORDER BY timestamp DESC, game_id DESC
            LIMIT %s
            """,
            (limit,),
        )
        rows = cursor.fetchall()
    # API accepts rounds in any order; stable order improves traceability.
    return [{"round_id": str(game_id), "multiplier": float(multiplier)}
            for game_id, multiplier in reversed(rows)]


def main():
    database_url = required("DATABASE_URL")
    api_url = required("CRASHAI_API_URL").rstrip("/")
    ingest_key = required("CRASHAI_INGEST_KEY")
    interval = max(2, int(os.getenv("RELAY_INTERVAL_SECONDS", "5")))
    endpoint = api_url + "/external-feed/import"

    with psycopg2.connect(database_url) as connection:
        while True:
            try:
                rounds = read_recent(connection)
                if rounds:
                    response = requests.post(
                        endpoint,
                        json={"source": "zakaria_postgres", "rounds": rounds},
                        headers={"X-CrashAI-Ingest-Key": ingest_key},
                        timeout=20,
                    )
                    response.raise_for_status()
                    result = response.json()
                    log.info("Relay accepted=%s duplicates=%s stored=%s",
                             result.get("accepted"), result.get("duplicates"),
                             result.get("stored_round_count"))
                else:
                    log.info("No completed rounds available yet.")
            except requests.RequestException as exc:
                # Do not print request headers or secret values.
                log.warning("CrashAI endpoint request failed: %s", type(exc).__name__)
            except psycopg2.Error as exc:
                log.warning("PostgreSQL read failed: %s", type(exc).__name__)
                raise
            time.sleep(interval)


if __name__ == "__main__":
    main()
