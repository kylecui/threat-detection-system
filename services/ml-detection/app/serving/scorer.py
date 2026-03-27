from __future__ import annotations


def clamp(value: float, lower: float, upper: float) -> float:
    return max(lower, min(upper, value))


def _interpolate(value: float, x0: float, x1: float, y0: float, y1: float) -> float:
    if x1 <= x0:
        return y0
    ratio = (value - x0) / (x1 - x0)
    return y0 + ratio * (y1 - y0)


def anomaly_type(score: float) -> str:
    if score < 0.3:
        return "normal"
    if score < 0.7:
        return "borderline"
    return "statistical_outlier"


def score_to_weight(score: float, confidence: float) -> float:
    if score < 0.3:
        base = _interpolate(score, 0.0, 0.3, 0.8, 1.0)
    elif score < 0.5:
        base = 1.0
    elif score < 0.7:
        base = _interpolate(score, 0.5, 0.7, 1.0, 1.5)
    elif score < 0.9:
        base = _interpolate(score, 0.7, 0.9, 1.5, 2.5)
    else:
        base = _interpolate(min(score, 1.2), 0.9, 1.2, 2.5, 3.0)

    conf = clamp(confidence, 0.0, 1.0)
    damped = 1.0 + (base - 1.0) * conf
    return clamp(damped, 0.5, 3.0)


def reconstruction_to_anomaly_score(reconstruction_error: float, threshold: float) -> float:
    safe_threshold = max(threshold, 1e-6)
    ratio = reconstruction_error / safe_threshold
    normalized = ratio / (1.0 + ratio)
    return clamp(normalized, 0.0, 1.0)
