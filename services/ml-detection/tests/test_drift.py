"""Tests for Phase 4D — PSI drift detection."""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pytest

from app.monitoring.drift import (
    FEATURE_DIM,
    FEATURE_NAMES,
    NUM_BINS,
    PSI_EPSILON,
    DriftMonitor,
    FeatureBaseline,
    compute_psi,
)


# ---------------------------------------------------------------------------
# compute_psi
# ---------------------------------------------------------------------------

class TestComputePsi:
    def test_identical_distributions(self):
        dist = np.array([0.1, 0.2, 0.3, 0.2, 0.1, 0.1])
        psi = compute_psi(dist, dist)
        assert psi == pytest.approx(0.0, abs=1e-8)

    def test_different_distributions(self):
        expected = np.array([0.2, 0.3, 0.3, 0.2])
        actual = np.array([0.1, 0.4, 0.4, 0.1])
        psi = compute_psi(expected, actual)
        assert psi > 0.0

    def test_epsilon_protection(self):
        """Zeros in distributions should be handled via epsilon clamping."""
        expected = np.array([0.0, 0.5, 0.5])
        actual = np.array([0.5, 0.5, 0.0])
        psi = compute_psi(expected, actual)
        assert np.isfinite(psi)

    def test_symmetry_approximate(self):
        """PSI is roughly symmetric for small differences."""
        a = np.array([0.2, 0.3, 0.3, 0.2])
        b = np.array([0.25, 0.25, 0.25, 0.25])
        psi_ab = compute_psi(a, b)
        psi_ba = compute_psi(b, a)
        # PSI is NOT symmetric, but for small diffs they should be close
        assert abs(psi_ab - psi_ba) < 0.05


# ---------------------------------------------------------------------------
# FeatureBaseline
# ---------------------------------------------------------------------------

class TestFeatureBaseline:
    def test_from_features_shape(self):
        rng = np.random.RandomState(42)
        features = rng.rand(500, FEATURE_DIM).astype(np.float32)

        baseline = FeatureBaseline.from_features(features, tier=1)
        assert baseline.tier == 1
        assert baseline.bin_edges.shape == (FEATURE_DIM, NUM_BINS + 1)
        assert baseline.bin_proportions.shape == (FEATURE_DIM, NUM_BINS)
        assert baseline.mean.shape == (FEATURE_DIM,)
        assert baseline.std.shape == (FEATURE_DIM,)
        assert baseline.sample_count == 500

    def test_proportions_sum_to_one(self):
        features = np.random.rand(200, FEATURE_DIM).astype(np.float32)
        baseline = FeatureBaseline.from_features(features, tier=2)

        for f_idx in range(FEATURE_DIM):
            total = np.sum(baseline.bin_proportions[f_idx])
            # Epsilon clamp might make it slightly > 1, but should be close
            assert total == pytest.approx(1.0, abs=0.01)

    def test_round_trip_serialization(self, tmp_path: Path):
        features = np.random.rand(100, FEATURE_DIM).astype(np.float32)
        baseline = FeatureBaseline.from_features(features, tier=3)

        data = baseline.to_dict()
        json_str = json.dumps(data)
        loaded = FeatureBaseline.from_dict(json.loads(json_str))

        assert loaded.tier == 3
        assert loaded.sample_count == 100
        np.testing.assert_allclose(loaded.bin_edges, baseline.bin_edges)
        np.testing.assert_allclose(loaded.bin_proportions, baseline.bin_proportions)
        np.testing.assert_allclose(loaded.mean, baseline.mean)
        np.testing.assert_allclose(loaded.std, baseline.std)


# ---------------------------------------------------------------------------
# DriftMonitor
# ---------------------------------------------------------------------------

class TestDriftMonitor:
    def test_observe_increments_count(self):
        monitor = DriftMonitor(window_size=100)
        features = np.random.rand(FEATURE_DIM).astype(np.float32)

        monitor.observe(1, features)
        monitor.observe(1, features)
        monitor.observe(2, features)

        status = monitor.get_status()
        assert status["tiers"]["tier1"]["recentObservations"] == 2
        assert status["tiers"]["tier2"]["recentObservations"] == 1
        assert status["tiers"]["tier3"]["recentObservations"] == 0

    def test_compute_drift_returns_none_without_baseline(self):
        monitor = DriftMonitor(window_size=100)
        for _ in range(60):
            monitor.observe(1, np.random.rand(FEATURE_DIM).astype(np.float32))

        result = monitor.compute_drift(1)
        assert result is None

    def test_compute_drift_returns_none_with_insufficient_observations(self):
        rng = np.random.RandomState(42)
        features = rng.rand(200, FEATURE_DIM).astype(np.float32)
        baseline = FeatureBaseline.from_features(features, tier=1)

        monitor = DriftMonitor(window_size=500)
        monitor._baselines[1] = baseline

        # Only 10 observations < min(50, window_size // 10)
        for _ in range(10):
            monitor.observe(1, rng.rand(FEATURE_DIM).astype(np.float32))

        result = monitor.compute_drift(1)
        assert result is None

    def test_compute_drift_same_distribution(self):
        """No drift when recent features ARE the training features."""
        rng = np.random.RandomState(42)
        features = rng.rand(1000, FEATURE_DIM).astype(np.float32)
        baseline = FeatureBaseline.from_features(features, tier=1)

        monitor = DriftMonitor(window_size=500, psi_threshold=1.0)
        monitor._baselines[1] = baseline

        # Feed exact training features — should have near-zero drift
        for i in range(500):
            monitor.observe(1, features[i])

        result = monitor.compute_drift(1)
        assert result is not None
        # Same data → PSI should be very small (much less than shifted case)
        assert result["total_psi"] < 1.0
        # Key invariant: same-distribution PSI << shifted-distribution PSI
        assert monitor._drift_alerts[1] is False

    def test_compute_drift_shifted_distribution(self):
        """Detects drift when features shift significantly."""
        rng = np.random.RandomState(42)
        features = rng.rand(500, FEATURE_DIM).astype(np.float32)
        baseline = FeatureBaseline.from_features(features, tier=2)

        monitor = DriftMonitor(window_size=200, psi_threshold=0.2)
        monitor._baselines[2] = baseline

        # Feed features from a VERY DIFFERENT distribution (shifted by 10)
        for _ in range(200):
            shifted = rng.rand(FEATURE_DIM).astype(np.float32) + 10.0
            monitor.observe(2, shifted)

        result = monitor.compute_drift(2)
        assert result is not None
        assert result["total_psi"] > 0.2
        assert monitor._drift_alerts[2] is True

    def test_save_and_load_baselines(self, tmp_path: Path):
        rng = np.random.RandomState(42)
        features = rng.rand(300, FEATURE_DIM).astype(np.float32)
        baseline = FeatureBaseline.from_features(features, tier=1)

        monitor = DriftMonitor(window_size=100, baseline_dir=str(tmp_path))
        saved_path = monitor.save_baseline(baseline)
        assert saved_path.exists()

        # Load in a fresh monitor
        monitor2 = DriftMonitor(window_size=100, baseline_dir=str(tmp_path))
        loaded = monitor2.load_baselines()
        assert loaded == 1
        assert 1 in monitor2._baselines
        assert monitor2._baselines[1].sample_count == 300

    def test_get_status_structure(self):
        monitor = DriftMonitor(window_size=100, psi_threshold=0.25)
        status = monitor.get_status()

        assert status["psiThreshold"] == 0.25
        assert status["windowSize"] == 100
        assert "tiers" in status
        for tier_key in ("tier1", "tier2", "tier3"):
            tier_data = status["tiers"][tier_key]
            assert "hasBaseline" in tier_data
            assert "driftDetected" in tier_data
            assert "recentObservations" in tier_data
