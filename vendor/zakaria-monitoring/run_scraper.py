"""Authorized live Crash round collector.

All account/session credentials are read from process environment only.
Never commit a populated .env file or print credential values.
"""
import asyncio
import json
import logging
import os
import time
from datetime import datetime, timezone
from urllib.parse import urlencode

import psycopg2
import websockets

LOG = logging.getLogger("zakaria-collector")
BASE_URL = "wss://ma-1xbet.com/games-frame/sockets/crash"


def required(name):
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def websocket_url():
    params = {
        "ref": os.getenv("WS_REF", "1"),
        "gr": os.getenv("WS_GR", "1529"),
        "whence": os.getenv("WS_WHENCE", "55"),
        "fcountry": os.getenv("WS_FCOUNTRY", "125"),
        "appGuid": os.getenv("WS_APP_GUID", "games-web-host-b2c-web-v3"),
        "lng": os.getenv("WS_LANGUAGE", "fr"),
        "v": os.getenv("WS_VERSION", "1.5"),
        "access_token": required("ZAKARIA_ACCESS_TOKEN"),
    }
    return BASE_URL + "?" + urlencode(params)


def headers():
    return {
        "Cookie": (
            "user_token=" + required("ZAKARIA_USER_TOKEN")
            + "; SESSION=" + required("ZAKARIA_SESSION")
        ),
        "Origin": "https://ma-1xbet.com",
        "User-Agent": os.getenv(
            "COLLECTOR_USER_AGENT",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        ),
    }


def save_round(game_id, multiplier, timestamp):
    database_url = required("DATABASE_URL")
    with psycopg2.connect(database_url) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """INSERT INTO crash_games (game_id, multiplier, timestamp, source)
                   VALUES (%s, %s, %s, 'xbet')
                   ON CONFLICT (game_id) DO NOTHING
                   RETURNING game_id""",
                (game_id, multiplier, timestamp),
            )
            return cursor.fetchone() is not None


async def connect():
    url = websocket_url()
    request_headers = headers()
    # websockets 11/12 use extra_headers; newer releases use additional_headers.
    try:
        return await websockets.connect(
            url, additional_headers=request_headers, ping_interval=30
        )
    except TypeError as exc:
        if "additional_headers" not in str(exc):
            raise
        return await websockets.connect(
            url, extra_headers=request_headers, ping_interval=30
        )


async def collect_once():
    account_id = required("ZAKARIA_ACCOUNT_ID")
    async with await connect() as ws:
        LOG.info("WebSocket connected; starting SignalR handshake.")
        await ws.send(json.dumps({"protocol": "json", "version": 1}) + "\x1e")
        await asyncio.wait_for(ws.recv(), timeout=15)
        await ws.send(json.dumps({
            "arguments": [{"activity": 30, "account": account_id}],
            "invocationId": "0",
            "target": "Account",
            "type": 1,
        }) + "\x1e")
        LOG.info("Subscription request sent; waiting for completed rounds.")
        while True:
            raw = await asyncio.wait_for(ws.recv(), timeout=90)
            for frame in raw.split("\x1e"):
                if not frame:
                    continue
                try:
                    message = json.loads(frame)
                except json.JSONDecodeError:
                    continue
                if message.get("type") == 6:
                    await ws.send(json.dumps({"type": 6}) + "\x1e")
                    continue
                if message.get("type") != 1 or message.get("target") != "OnCrash":
                    continue
                args = message.get("arguments") or []
                if not args:
                    continue
                item = args[0]
                try:
                    game_id = str(item["l"])
                    multiplier = float(item["f"])
                    timestamp_ms = int(item["ts"])
                    if multiplier < 1:
                        raise ValueError("multiplier below 1")
                    timestamp = datetime.fromtimestamp(
                        timestamp_ms / 1000, tz=timezone.utc
                    ).replace(tzinfo=None)
                    inserted = save_round(game_id, multiplier, timestamp)
                    LOG.info("Completed round received; stored=%s", inserted)
                except (KeyError, TypeError, ValueError, OverflowError):
                    LOG.warning("Skipped malformed completed-round message.")


async def main():
    required("DATABASE_URL")
    # Validate all credential variables before attempting network access.
    required("ZAKARIA_USER_TOKEN")
    required("ZAKARIA_SESSION")
    required("ZAKARIA_ACCESS_TOKEN")
    required("ZAKARIA_ACCOUNT_ID")
    delay = max(5, int(os.getenv("COLLECTOR_RECONNECT_SECONDS", "15")))
    while True:
        try:
            await collect_once()
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            LOG.warning("Collector disconnected (%s); retrying.", type(exc).__name__)
            await asyncio.sleep(delay)


if __name__ == "__main__":
    logging.basicConfig(
        level=os.getenv("LOG_LEVEL", "INFO"),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        LOG.info("Collector stopped.")
