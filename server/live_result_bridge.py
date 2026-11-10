"""Read-only live result fan-out for connected mobile clients.

Only completed, validated outcomes are published. This module does not predict
or infer a future result and never handles provider credentials.
"""
from datetime import datetime, timezone

_clients = set()


async def subscribe(websocket):
    _clients.add(websocket)


def unsubscribe(websocket):
    _clients.discard(websocket)


async def broadcast_result(round_id: str, multiplier: float, source: str):
    event = {
        "type": "live_result",
        "round_id": round_id,
        "actual_multiplier": round(float(multiplier), 4),
        "source": source,
        "confirmed": True,
        "occurred_at": datetime.now(timezone.utc).isoformat(),
        "prediction": None,
        "note": "Completed outcome received; this is not a prediction."
    }
    stale = []
    for client in tuple(_clients):
        try:
            await client.send_json(event)
        except Exception:
            stale.append(client)
    for client in stale:
        _clients.discard(client)
    return event


def subscriber_count():
    return len(_clients)
