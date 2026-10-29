"""Three complementary, bounded analysis views over confirmed Crash rounds."""
from math import log, sqrt
from statistics import mean, median, pstdev

def _wilson(hits, n):
    if not n:
        return None
    p = hits/n
    z = 1.96
    denominator = 1 + z*z/n
    center = (p + z*z/(2*n))/denominator
    spread = z*sqrt(p*(1-p)/n + z*z/(4*n*n))/denominator
    return [round(max(0, center-spread)*100, 2),
            round(min(1, center+spread)*100, 2)]

def analyze_three_ways(rounds):
    if not rounds:
        return {"count": 0, "available": False}
    # 1. Global distribution, robust to extreme values.
    sorted_values = sorted(rounds)
    n = len(rounds)
    global_view = {
        "count": n, "median": round(median(rounds), 3),
        "arithmetic_mean": round(mean(rounds), 3),
        "log_mean": round(mean(log(v) for v in rounds), 4),
        "population_std_dev": round(pstdev(rounds), 3),
        "p90": round(sorted_values[min(n-1, int(0.9*(n-1)))], 3),
    }
    # 2. Recent windows, with identical definitions for comparison.
    windows = {}
    for size in (20, 50, 100):
        subset = rounds[-size:]
        windows[str(size)] = {
            "count": len(subset),
            "median": round(median(subset), 3),
            "at_least_2x_percent": round(100*sum(v >= 2 for v in subset)/len(subset), 2),
            "complete_window": len(subset) >= size,
        }
    # 3. Threshold frequency and uncertainty intervals.
    thresholds = {}
    for value in (2, 5, 10):
        hits = sum(v >= value for v in rounds)
        thresholds[f"{value}x"] = {
            "hits": hits, "observed_percent": round(100*hits/n, 2),
            "wilson_95_percent": _wilson(hits, n),
        }
    return {
        "available": True,
        "global_distribution": global_view,
        "rolling_windows": windows,
        "threshold_confidence": thresholds,
        "warning": "These describe completed rounds, not a known future multiplier.",
    }
