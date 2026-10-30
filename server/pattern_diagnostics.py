"""Tests for temporal structure in stored Crash outcomes; no guaranteed prediction."""
from math import isfinite, log
from statistics import mean

def inspect_structure(values):
    data = [float(x) for x in values if isfinite(float(x)) and float(x) >= 1]
    n = len(data)
    if n < 30:
        return {"ready": False, "sample_count": n, "required": 30}
    # Log transform limits the influence of large Crash outliers.
    xs = [log(x) for x in data]
    avg = mean(xs)
    variance = sum((x-avg)**2 for x in xs)
    lags = {}
    for lag in (1, 2, 3, 5, 10, 20):
        if n <= lag + 10: continue
        a, b = xs[:-lag], xs[lag:]
        ma, mb = mean(a), mean(b)
        den = (sum((x-ma)**2 for x in a)*sum((y-mb)**2 for y in b))**0.5
        lags[str(lag)] = round(sum((x-ma)*(y-mb) for x,y in zip(a,b))/den,4) if den else 0
    # Walk-forward test, comparing previous-result rule to historical median baseline.
    baseline_errors, lag_errors = [], []
    for i in range(20, n):
        past = sorted(xs[:i])
        baseline = past[len(past)//2]
        baseline_errors.append(abs(xs[i]-baseline))
        lag_errors.append(abs(xs[i]-xs[i-1]))
    baseline_mae = mean(baseline_errors)
    lag_mae = mean(lag_errors)
    return {
        "ready": True, "sample_count": n,
        "log_autocorrelation_by_lag": lags,
        "walk_forward_log_mae_baseline": round(baseline_mae,4),
        "walk_forward_log_mae_previous_round": round(lag_mae,4),
        "previous_round_beats_baseline": lag_mae < baseline_mae,
        "evidence_of_predictability": "unproven",
        "note": "Correlations and retrospective tests do not prove future outcomes can be predicted."
    }
