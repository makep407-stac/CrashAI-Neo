"""Three-stage screen OCR interpretation (no screen capture in this module).

Android MediaProjection and OCR integration must supply text frames with round IDs.
"""
import re
from collections import Counter

_PATTERN = re.compile(r"(?<!\d)(\d{1,7}(?:[.,]\d{1,4})?)\s*[x×](?!\w)", re.I)
def normalize_text(text):
    return text.replace("，", ",").replace("٫", ".").replace("Ｘ", "x").strip()

def extract_candidates(text):
    """Strategy 1: locate explicitly suffixed multipliers in an OCR frame."""
    return [float(x.replace(",", ".")) for x in _PATTERN.findall(normalize_text(text))
            if 1 <= float(x.replace(",", ".")) < 10000000]

def consensus(frames):
    """Strategy 2: temporal voting over multiple OCR frames."""
    observations = []
    for frame in frames[-10:]:
        candidates = extract_candidates(frame)
        if len(candidates) == 1:
            observations.append(round(candidates[0], 2))
    if not observations:
        return {"value": None, "status": "unreadable", "votes": 0}
    value, votes = Counter(observations).most_common(1)[0]
    if votes < 2:
        return {"value": None, "status": "needs_confirmation", "votes": votes}
    return {"value": value, "status": "confirmed_ocr", "votes": votes}

def validate_completed_round(round_id, frames, phase):
    """Strategy 3: state gating; do not treat in-flight frames as final outcomes."""
    if phase != "finished" or not round_id:
        return {"accepted": False, "reason": "Round must have an ID and be finished."}
    result = consensus(frames)
    return {"accepted": result["status"] == "confirmed_ocr",
            "round_id": round_id, **result,
            "note": "OCR consensus is not independent proof of the final result."}
