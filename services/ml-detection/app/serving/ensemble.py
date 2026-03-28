"""Ensemble scoring — weighted geometric mean of autoencoder + BiGRU scores.

Formula:
    combined = (ae_score ** alpha) * (clamp(bigru_pred, 0.01, 1.0) ** (1 - alpha))

Cold-start fallback: when sequence length < min_seq_len or BiGRU prediction
is unavailable, the autoencoder score is returned unchanged.
"""

from __future__ import annotations

from typing import Optional, Tuple


def ensemble_anomaly_score(
    ae_score: float,
    bigru_pred: Optional[float],
    seq_len: int,
    min_seq_len: int = 4,
    alpha: float = 0.6,
) -> Tuple[float, str]:
    """Combine autoencoder and BiGRU anomaly scores.

    Args:
        ae_score: Autoencoder anomaly score in [0, 1].
        bigru_pred: BiGRU predicted next-window anomaly score, or None.
        seq_len: Current sequence length for this attacker.
        min_seq_len: Minimum windows required before BiGRU activates.
        alpha: Autoencoder weight in geometric mean (0.6 = autoencoder-dominant).

    Returns:
        (combined_score, method) where method is "autoencoder_only" or "ensemble".
    """
    # Clamp ae_score to valid range
    ae_score = max(0.0, min(1.0, ae_score))

    # Cold start — not enough history or no BiGRU prediction
    if bigru_pred is None or seq_len < min_seq_len:
        return ae_score, "autoencoder_only"

    # Clamp BiGRU prediction to avoid log(0) in geometric mean
    bigru_clamped = max(0.01, min(1.0, float(bigru_pred)))

    # Weighted geometric mean
    combined = (ae_score ** alpha) * (bigru_clamped ** (1.0 - alpha))

    # Final clamp
    combined = max(0.0, min(1.0, combined))

    return combined, "ensemble"
