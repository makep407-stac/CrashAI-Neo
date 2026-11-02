import asyncio
import hmac
import os
import time
from collections import deque
from typing import Any, Dict, Optional
from uuid import uuid4

from fastapi import APIRouter, Header, HTTPException, WebSocket, WebSocketDisconnect
from pydantic import BaseModel, Field, field_validator

router = APIRouter()
MAX_ROUNDS = 5000
rounds = deque(maxlen=MAX_ROUNDS)
seen_ids = set()
clients = set()
lock = asyncio.Lock()


class RoundEvent(BaseModel):
    multiplier: float = Field(gt=0, le=1_000_000)
    event_id: str = Field(default_factory=lambda: str(uuid4()), min_length=1, max_length=128)
    source: str = Field(default="manual", min_length=1, max_length=40)
    occurred_at: Optional[float] = None

    @field_validator("multiplier")
    @classmethod
    def finite_multiplier(cls, value):
        if value != value or value in (float("inf"), float("-inf")):
            raise ValueError("multiplier must be finite")
        return value


def require_ingest_key(authorization: Optional[str]) -> None:
    expected = os.getenv("CRASH_INGEST_API_KEY", "")
    if not expected:
        raise HTTPException(
            status_code=503,
            detail="Ingestion is disabled until CRASH_INGEST_API_KEY is configured."
        )
    supplied = (authorization or "").removeprefix("Bearer ").strip()
    if not hmac.compare_digest(supplied, expected):
        raise HTTPException(status_code=401, detail="Invalid ingestion credentials.")


async def publish(event: RoundEvent) -> Dict[str, Any]:
    payload = {
        "type": "round",
        "event_id": event.event_id,
        "multiplier": event.multiplier,
        "source": event.source,
        "occurred_at": event.occurred_at or time.time(),
        "received_at": time.time(),
    }
    async with lock:
        if event.event_id in seen_ids:
            return {"accepted": False, "duplicate": True, "event": payload}
        if len(rounds) == MAX_ROUNDS:
            old = rounds[0]
            seen_ids.discard(old["event_id"])
        rounds.append(payload)
        seen_ids.add(event.event_id)
        recipients = list(clients)

    stale = []
    for socket in recipients:
        try:
            await socket.send_json(payload)
        except Exception:
            stale.append(socket)
    if stale:
        async with lock:
            for socket in stale:
                clients.discard(socket)
    return {"accepted": True, "duplicate": False, "event": payload}


@router.post("/ingest/round")
async def ingest_round(
    event: RoundEvent,
    authorization: Optional[str] = Header(default=None),
):
    require_ingest_key(authorization)
    return await publish(event)


@router.get("/rounds/latest")
async def latest_rounds(limit: int = 50):
    if limit < 1 or limit > 500:
        raise HTTPException(status_code=400, detail="limit must be between 1 and 500")
    async with lock:
        items = list(rounds)[-limit:]
    return {"count": len(items), "rounds": items}


@router.websocket("/ws/rounds")
async def rounds_socket(websocket: WebSocket):
    expected = os.getenv("CRASH_INGEST_API_KEY", "")
    supplied = websocket.headers.get("authorization", "").removeprefix("Bearer ").strip()
    if not expected or not hmac.compare_digest(supplied, expected):
        await websocket.close(code=1008, reason="Unauthorized")
        return

    await websocket.accept()
    async with lock:
        clients.add(websocket)
        snapshot = list(rounds)[-50:]
    try:
        await websocket.send_json({"type": "snapshot", "rounds": snapshot})
        while True:
            # Keep the connection alive; clients may send a ping message.
            await websocket.receive_text()
    except WebSocketDisconnect:
        pass
    finally:
        async with lock:
            clients.discard(websocket)
