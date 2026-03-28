"""Tests for ensemble scoring — weighted geometric mean of AE + BiGRU."""

from __future__ import annotations

import pytest

from app.serving.ensemble import ensemble_anomaly_score


class TestColdStart:
    """When BiGRU is not available, return autoencoder score unchanged."""

    def test_bigru_pred_none(self):
        score, method = ensemble_anomaly_score(0.7, None, seq_len=10)
        assert score == pytest.approx(0.7)
        assert method == "autoencoder_only"

    def test_seq_len_below_min(self):
        score, method = ensemble_anomaly_score(0.7, 0.5, seq_len=3, min_seq_len=4)
        assert score == pytest.approx(0.7)
        assert method == "autoencoder_only"

    def test_seq_len_zero(self):
        score, method = ensemble_anomaly_score(0.5, 0.5, seq_len=0)
        assert score == pytest.approx(0.5)
        assert method == "autoencoder_only"


class TestEnsemble:
    """Full ensemble with both scores available and sufficient history."""

    def test_basic_ensemble(self):
        # ae=0.8, bigru=0.6, alpha=0.6
        # expected = 0.8^0.6 * 0.6^0.4
        expected = (0.8 ** 0.6) * (0.6 ** 0.4)
        score, method = ensemble_anomaly_score(0.8, 0.6, seq_len=10, alpha=0.6)
        assert score == pytest.approx(expected, abs=1e-6)
        assert method == "ensemble"

    def test_equal_scores(self):
        # When both scores are equal, geometric mean = that score
        score, method = ensemble_anomaly_score(0.5, 0.5, seq_len=10, alpha=0.6)
        assert score == pytest.approx(0.5, abs=1e-6)
        assert method == "ensemble"

    def test_both_ones(self):
        score, method = ensemble_anomaly_score(1.0, 1.0, seq_len=10)
        assert score == pytest.approx(1.0, abs=1e-6)
        assert method == "ensemble"

    def test_alpha_dominance(self):
        """Higher alpha = more autoencoder influence."""
        # ae=0.9, bigru=0.1 → with high alpha, closer to ae
        score_high_alpha, _ = ensemble_anomaly_score(0.9, 0.1, seq_len=10, alpha=0.9)
        score_low_alpha, _ = ensemble_anomaly_score(0.9, 0.1, seq_len=10, alpha=0.1)
        assert score_high_alpha > score_low_alpha

    def test_at_exact_min_seq_len(self):
        """seq_len == min_seq_len should activate ensemble."""
        score, method = ensemble_anomaly_score(0.8, 0.6, seq_len=4, min_seq_len=4)
        assert method == "ensemble"


class TestEdgeCases:
    """Boundary values and clamping."""

    def test_bigru_zero_clamped_to_001(self):
        # bigru=0.0 → clamped to 0.01 to avoid log(0)
        score, method = ensemble_anomaly_score(0.5, 0.0, seq_len=10, alpha=0.6)
        expected = (0.5 ** 0.6) * (0.01 ** 0.4)
        assert score == pytest.approx(expected, abs=1e-6)
        assert method == "ensemble"

    def test_bigru_negative_clamped(self):
        score, method = ensemble_anomaly_score(0.5, -0.5, seq_len=10, alpha=0.6)
        expected = (0.5 ** 0.6) * (0.01 ** 0.4)
        assert score == pytest.approx(expected, abs=1e-6)

    def test_ae_score_zero(self):
        score, method = ensemble_anomaly_score(0.0, 0.5, seq_len=10)
        assert score == pytest.approx(0.0)
        assert method == "ensemble"

    def test_ae_score_above_one_clamped(self):
        score, method = ensemble_anomaly_score(1.5, 0.5, seq_len=10)
        expected = (1.0 ** 0.6) * (0.5 ** 0.4)
        assert score == pytest.approx(expected, abs=1e-6)

    def test_result_always_in_0_1(self):
        """Fuzz: all outputs should be in [0, 1]."""
        import random
        random.seed(42)
        for _ in range(100):
            ae = random.uniform(-0.5, 1.5)
            bigru = random.uniform(-0.5, 1.5)
            seq = random.randint(0, 50)
            score, _ = ensemble_anomaly_score(ae, bigru, seq_len=seq, alpha=0.6)
            assert 0.0 <= score <= 1.0, f"Out of range: {score} for ae={ae}, bigru={bigru}"


class TestWeightRange:
    """Ensemble output feeds into score_to_weight — verify score range is valid."""

    def test_ensemble_score_compatible_with_scorer(self):
        """Ensemble output should be in [0, 1] — valid input for score_to_weight."""
        test_cases = [
            (0.1, 0.1, 10),
            (0.5, 0.5, 10),
            (0.9, 0.9, 10),
            (0.1, 0.9, 10),
            (0.9, 0.1, 10),
        ]
        for ae, bigru, seq in test_cases:
            score, _ = ensemble_anomaly_score(ae, bigru, seq_len=seq)
            assert 0.0 <= score <= 1.0
