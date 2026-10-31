"""Chronological model selection for a single Crash multiplier estimate.

This is a transparent statistical ensemble, not a trained neural network.
It only reports an empirical edge when walk-forward performance beats
the robust median baseline by a meaningful margin.
"""
from math import exp, log
from statistics import median, mean


def _clip(v):
    return min(20.0, max(1.0, float(v)))


def _models(history):
    h = [_clip(v) for v in history]
    recent = h[-min(20, len(h)):]
    logs = [log(v) for v in recent]
    # Candidate A: robust median over recent history.
    recent_median = median(recent)
    # Candidate B: geometric mean in log space, capped against outliers.
    log_mean = exp(mean(logs))
    # Candidate C: exponentially weighted log estimate.
    weights = [0.88 ** (len(logs) - 1 - i) for i in range(len(logs))]
    ewma = exp(sum(w*x for w, x in zip(weights, logs)) / sum(weights))
    return {
        "median_baseline": median(h[-min(100, len(h)):]),
        "recent_median": recent_median,
        "log_mean_20": log_mean,
        "log_ewma": ewma,
    }


def select_single_estimate(rounds):
    values = [_clip(v) for v in rounds if isinstance(v, (int, float)) and v >= 1]
    if not values:
        return {
            "estimate_multiplier": 2.0, "selected_model": "uncalibrated_reference",
            "sample_count": 0, "walk_forward_samples": 0,
            "baseline_log_mae": None, "selected_log_mae": None,
            "improvement_percent": None, "validated_predictive_edge": False,
            "status": "insufficient_data",
        }
    candidates = ("recent_median", "log_mean_20", "log_ewma")
    errors = {name: [] for name in ("median_baseline",) + candidates}
    # Test each forecast using only earlier rounds; never leak the target round.
    start = max(30, len(values) - 150)
    for index in range(start, len(values)):
        prediction = _models(values[:index])
        actual = log(values[index])
        for name in errors:
            errors[name].append(abs(log(_clip(prediction[name])) - actual))
    baseline_error = mean(errors["median_baseline"]) if errors["median_baseline"] else None
    scored = [(mean(err), name) for name, err in errors.items() if name != "median_baseline" and err]
    chosen_error, chosen_name = min(scored) if scored else (None, "median_baseline")
    improvement = ((baseline_error - chosen_error) / baseline_error * 100
                   if baseline_error and chosen_error is not None else None)
    edge = bool(len(errors["median_baseline"]) >= 60 and improvement is not None and improvement >= 5.0)
    model_name = chosen_name if edge else "median_baseline"
    current = _models(values)[model_name]
    return {
        "estimate_multiplier": round(_clip(current), 2),
        "selected_model": model_name,
        "sample_count": len(values),
        "walk_forward_samples": len(errors["median_baseline"]),
        "baseline_log_mae": round(baseline_error, 5) if baseline_error is not None else None,
        "selected_log_mae": round(chosen_error, 5) if chosen_error is not None else None,
        "improvement_percent": round(improvement, 2) if improvement is not None else None,
        "validated_predictive_edge": edge,
        "status": "candidate_beats_baseline" if edge else "no_proven_edge_baseline_selected",
        "note": "A historical edge is not a guarantee and can disappear on future rounds.",
    }
