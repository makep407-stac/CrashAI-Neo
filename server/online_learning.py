"""Online descriptive learning from confirmed completed rounds.

In-memory only: a restart resets state. Use durable storage before production.
No ability to infer independent future game outcomes is implied.
"""
from collections import deque
from math import isfinite, sqrt
from threading import Lock

class OnlineLearner:
    def __init__(self, capacity=500):
        self._lock = Lock()
        self._rounds = deque(maxlen=capacity)
        self._ids = set()
        self._evaluations = deque(maxlen=capacity)

    @staticmethod
    def _estimate(hits, count):
        # Beta(1,1) smoothed observed frequency, not a game-specific model.
        return (hits + 1) / (count + 2)

    def add(self, round_id, multiplier):
        if not isinstance(round_id, str) or not round_id.strip() or len(round_id) > 128:
            raise ValueError("A nonempty round_id (max 128 chars) is required.")
        if isinstance(multiplier, bool) or not isinstance(multiplier, (int, float)) or not isfinite(multiplier) or multiplier < 1:
            raise ValueError("Final multiplier must be finite and >= 1x.")
        with self._lock:
            if round_id in self._ids:
                return {"status": "duplicate_ignored", **self._snapshot()}
            previous = list(self._rounds)
            if previous:
                for threshold in (2, 5, 10):
                    probability = self._estimate(sum(v >= threshold for _, v in previous), len(previous))
                    outcome = int(multiplier >= threshold)
                    self._evaluations.append((threshold, (probability - outcome) ** 2))
            if len(self._rounds) == self._rounds.maxlen:
                old_id, _ = self._rounds.popleft()
                self._ids.remove(old_id)
            self._rounds.append((round_id, float(multiplier)))
            self._ids.add(round_id)
            return {"status": "learned", **self._snapshot()}

    def status(self):
        with self._lock:
            return self._snapshot()

    def _snapshot(self):
        values = [value for _, value in self._rounds]
        count = len(values)
        thresholds = {}
        for threshold in (2, 5, 10):
            hits = sum(value >= threshold for value in values)
            errors = [error for t, error in self._evaluations if t == threshold]
            thresholds[f"{threshold}x"] = {
                "historical_observed_percent": round(100 * hits / count, 2) if count else None,
                "smoothed_historical_percent": round(100 * self._estimate(hits, count), 2) if count else None,
                "prequential_brier_score": round(sum(errors) / len(errors), 5) if errors else None,
                "evaluated_rounds": len(errors),
            }
        return {
            "sample_size": count,
            "storage": "memory_only",
            "thresholds": thresholds,
            "prediction_available": False,
            "message": "Online calibration of historical frequencies; not a reliable next-round forecast."
        }

learner = OnlineLearner()
