"""Observational reverse engineering of public Crash multiplier histories.

This module does not access game servers, session tokens, or future outcomes.
Tests describe historical data; apparent patterns can occur by chance.
"""
from math import sqrt
from statistics import mean


def inspect_rounds(rounds):
    n = len(rounds)
    if n < 2:
        return {
            "sample_size": n,
            "sufficient_for_tests": False,
            "message": "At least two completed rounds are needed."
        }

    def lag1(values):
        if len(values) < 3:
            return None
        left, right = values[:-1], values[1:]
        a, b = mean(left), mean(right)
        numerator = sum((x-a)*(y-b) for x, y in zip(left, right))
        denominator = sqrt(
            sum((x-a)**2 for x in left) * sum((y-b)**2 for y in right)
        )
        return round(numerator/denominator, 4) if denominator else None

    # Log compression prevents a few huge payouts dominating the diagnostic.
    from math import log
    transformed = [log(value) for value in rounds]
    binary = [int(value >= 2) for value in rounds]
    switches = sum(a != b for a, b in zip(binary, binary[1:]))
    p = mean(binary)
    expected_switches = 2*p*(1-p)*(n-1)
    longest_low = longest_high = run_low = run_high = 0
    for flag in binary:
        run_high = run_high + 1 if flag else 0
        run_low = run_low + 1 if not flag else 0
        longest_high = max(longest_high, run_high)
        longest_low = max(longest_low, run_low)

    midpoint = n // 2
    first_rate = mean(binary[:midpoint])
    second_rate = mean(binary[midpoint:])
    return {
        "sample_size": n,
        "sufficient_for_tests": n >= 30,
        "lag1_log_multiplier_correlation": lag1(transformed),
        "lag1_at_least_2x_correlation": lag1(binary),
        "at_least_2x_switch_count": switches,
        "expected_switch_count_under_independence": round(expected_switches, 2),
        "longest_below_2x_streak": longest_low,
        "longest_at_least_2x_streak": longest_high,
        "first_half_at_least_2x_percent": round(first_rate*100, 2),
        "second_half_at_least_2x_percent": round(second_rate*100, 2),
        "note": (
            "Exploratory diagnostics, not proof of nonrandomness. "
            "No inference of hidden seeds, future outcomes, or guaranteed predictions."
        )
    }
