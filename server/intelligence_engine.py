"""Adaptive, leakage-safe forecasting diagnostics for CrashAI Neo.

This is an online statistical ensemble, not a neural network. It never claims
that independent random outcomes are predictable. It selects a point estimator
only when chronological validation supports it and reports threshold rates as
historical probabilities with uncertainty, not guarantees.
"""
from math import exp, log, sqrt
from statistics import mean, median


def _clean(values):
    return [min(1000.0, max(1.0, float(v))) for v in values
            if isinstance(v, (int, float)) and 1.0 <= float(v) <= 100000.0]


def _point_candidates(history):
    h = _clean(history)
    if not h:
        return {}
    recent = h[-min(50, len(h)):]
    logs = [log(v) for v in recent]
    weights = [0.92 ** (len(logs) - 1 - i) for i in range(len(logs))]
    ewma = exp(sum(w*x for w, x in zip(weights, logs)) / sum(weights))
    # Robust estimates and a recency-weighted estimate, all in log space.
    return {
        "median_100": median(h[-min(100, len(h)):]),
        "median_20": median(h[-min(20, len(h)):]),
        "geometric_20": exp(mean(logs[-20:])),
        "log_ewma": ewma,
    }


def _wilson(successes, total, z=1.96):
    if total <= 0:
        return [0.0, 100.0]
    p = successes / total
    d = 1 + z*z/total
    c = (p + z*z/(2*total))/d
    r = z*sqrt((p*(1-p)+z*z/(4*total))/total)/d
    return [round(max(0, c-r)*100, 2), round(min(1, c+r)*100, 2)]


def analyze_intelligence(history, max_backtest=250):
    values = _clean(history)
    n = len(values)
    if n < 10:
        return {
            "status": "collecting_data", "sample_count": n,
            "required_samples": 10, "point_forecast": None,
            "validated_edge": False, "thresholds": {},
            "message": "Insufficient history. No numeric forecast is issued."
        }

    # Strict walk-forward: each prediction uses only rounds before its target.
    start = max(30, n - max_backtest)
    errors = {k: [] for k in ("median_100", "median_20", "geometric_20", "log_ewma")}
    for i in range(start, n):
        candidates = _point_candidates(values[:i])
        actual = log(values[i])
        for name, prediction in candidates.items():
            errors[name].append(abs(log(max(1.0, prediction)) - actual))

    scored = [(mean(v), k) for k, v in errors.items() if v]
    scored.sort()
    baseline = mean(errors["median_100"]) if errors["median_100"] else None
    best_error, best_name = scored[0] if scored else (None, "median_100")
    improvement = ((baseline - best_error) / baseline * 100
                   if baseline and best_error is not None else None)
    tested = len(errors["median_100"])
    edge = bool(tested >= 60 and improvement is not None and improvement >= 5.0)
    chosen = best_name if edge else "median_100"
    current = _point_candidates(values)[chosen]

    thresholds = {}
    for threshold in (1.5, 2, 3, 5, 10):
        hits = sum(v >= threshold for v in values)
        # Jeffreys prior avoids overconfident 0% / 100% from small samples.
        rate = (hits + 0.5) / (n + 1)
        thresholds[str(threshold)] = {
            "historical_rate_percent": round(hits/n*100, 2),
            "smoothed_rate_percent": round(rate*100, 2),
            "wilson_95_percent": _wilson(hits, n),
            "observations": hits,
        }

    return {
        "status": "validated_candidate" if edge else "no_proven_edge",
        "sample_count": n,
        "walk_forward_tests": tested,
        "point_forecast": round(current, 2),
        "selected_model": chosen,
        "validated_edge": edge,
        "baseline_log_mae": round(baseline, 5) if baseline is not None else None,
        "best_candidate_log_mae": round(best_error, 5) if best_error is not None else None,
        "improvement_percent": round(improvement, 2) if improvement is not None else None,
        "candidate_scores": {
            name: {"log_mae": round(mean(err), 5), "tests": len(err)}
            for name, err in errors.items() if err
        },
        "thresholds": thresholds,
        "interpretation": (
            "Chronological tests show a measurable historical edge; future performance is not guaranteed."
            if edge else
            "No candidate proved a meaningful edge over the robust baseline. Point value is descriptive only."
        ),
        "limitations": "If rounds are independently generated, past multipliers do not reveal the next result. Threshold rates describe history, not a guaranteed next-round probability."
    }
