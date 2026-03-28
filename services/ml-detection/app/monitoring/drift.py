"""Population Stability Index (PSI) drift detection for ML features.

Compares recent inference feature distributions against stored training baselines.
PSI > 0.1 = slight drift, PSI > 0.2 = significant drift requiring attention.
"""

from __future__ import annotations

import json
import logging
import time
from collections import deque
from pathlib import Path
from typing import Deque, Dict, List, Optional, Tuple

import numpy as np

logger = logging.getLogger(__name__)

FEATURE_DIM = 12
NUM_BINS = 10
PSI_EPSILON = 1e-6

FEATURE_NAMES = [
    "attack_count_log", "unique_ips", "unique_ports", "unique_devices_norm",
    "mixed_port_weight", "net_weight_log", "intel_score_norm", "event_time_span_log",
    "burst_intensity", "time_dist_weight", "hour_sin", "hour_cos",
]


class FeatureBaseline:
    def __init__(
        self,
        tier: int,
        bin_edges: np.ndarray,
        bin_proportions: np.ndarray,
        mean: np.ndarray,
        std: np.ndarray,
        sample_count: int,
    ) -> None:
        self.tier = tier
        self.bin_edges = bin_edges
        self.bin_proportions = bin_proportions
        self.mean = mean
        self.std = std
        self.sample_count = sample_count

    def to_dict(self) -> dict:
        return {
            "tier": self.tier,
            "bin_edges": self.bin_edges.tolist(),
            "bin_proportions": self.bin_proportions.tolist(),
            "mean": self.mean.tolist(),
            "std": self.std.tolist(),
            "sample_count": self.sample_count,
        }

    @classmethod
    def from_dict(cls, data: dict) -> FeatureBaseline:
        return cls(
            tier=data["tier"],
            bin_edges=np.array(data["bin_edges"], dtype=np.float64),
            bin_proportions=np.array(data["bin_proportions"], dtype=np.float64),
            mean=np.array(data["mean"], dtype=np.float64),
            std=np.array(data["std"], dtype=np.float64),
            sample_count=data["sample_count"],
        )

    @classmethod
    def from_features(cls, features: np.ndarray, tier: int, num_bins: int = NUM_BINS) -> FeatureBaseline:
        """Build baseline statistics from training feature matrix (N, 12)."""
        mean = np.mean(features, axis=0)
        std = np.std(features, axis=0)

        all_edges = np.zeros((FEATURE_DIM, num_bins + 1), dtype=np.float64)
        all_proportions = np.zeros((FEATURE_DIM, num_bins), dtype=np.float64)

        for f_idx in range(FEATURE_DIM):
            col = features[:, f_idx]
            percentiles = np.linspace(0, 100, num_bins + 1)
            edges = np.percentile(col, percentiles)
            edges[0] = -np.inf
            edges[-1] = np.inf
            counts, _ = np.histogram(col, bins=edges)
            proportions = counts / max(len(col), 1)
            proportions = np.maximum(proportions, PSI_EPSILON)
            all_edges[f_idx] = edges
            all_proportions[f_idx] = proportions

        return cls(
            tier=tier,
            bin_edges=all_edges,
            bin_proportions=all_proportions,
            mean=mean,
            std=std,
            sample_count=len(features),
        )


def compute_psi(expected: np.ndarray, actual: np.ndarray) -> float:
    """PSI = Σ (actual_i - expected_i) * ln(actual_i / expected_i)"""
    expected_safe = np.maximum(expected, PSI_EPSILON)
    actual_safe = np.maximum(actual, PSI_EPSILON)
    return float(np.sum((actual_safe - expected_safe) * np.log(actual_safe / expected_safe)))


class DriftMonitor:
    def __init__(
        self,
        window_size: int = 500,
        psi_threshold: float = 0.2,
        baseline_dir: Optional[str] = None,
    ) -> None:
        self._window_size = window_size
        self._psi_threshold = psi_threshold
        self._baseline_dir = Path(baseline_dir) if baseline_dir else None
        self._baselines: Dict[int, FeatureBaseline] = {}
        self._recent_features: Dict[int, Deque[np.ndarray]] = {
            tier: deque(maxlen=window_size) for tier in (1, 2, 3)
        }
        self._last_psi: Dict[int, Dict[str, float]] = {}
        self._drift_alerts: Dict[int, bool] = {1: False, 2: False, 3: False}
        self._total_observations: Dict[int, int] = {1: 0, 2: 0, 3: 0}

    def load_baselines(self) -> int:
        if self._baseline_dir is None or not self._baseline_dir.exists():
            return 0
        loaded = 0
        for tier in (1, 2, 3):
            path = self._baseline_dir / f"baseline_tier{tier}.json"
            if not path.exists():
                continue
            try:
                data = json.loads(path.read_text())
                self._baselines[tier] = FeatureBaseline.from_dict(data)
                loaded += 1
                logger.info("Loaded drift baseline for tier %d (%d samples)", tier, data["sample_count"])
            except Exception as exc:
                logger.warning("Failed to load baseline for tier %d: %s", tier, exc)
        return loaded

    def save_baseline(self, baseline: FeatureBaseline) -> Path:
        if self._baseline_dir is None:
            raise ValueError("baseline_dir not configured")
        self._baseline_dir.mkdir(parents=True, exist_ok=True)
        path = self._baseline_dir / f"baseline_tier{baseline.tier}.json"
        path.write_text(json.dumps(baseline.to_dict(), indent=2))
        self._baselines[baseline.tier] = baseline
        logger.info("Saved drift baseline for tier %d to %s", baseline.tier, path)
        return path

    def observe(self, tier: int, features: np.ndarray) -> None:
        self._recent_features[tier].append(features.copy())
        self._total_observations[tier] += 1

    def compute_drift(self, tier: int) -> Optional[Dict[str, float]]:
        baseline = self._baselines.get(tier)
        if baseline is None:
            return None

        recent = self._recent_features.get(tier)
        if recent is None or len(recent) < max(50, self._window_size // 10):
            return None

        recent_matrix = np.stack(list(recent), axis=0)
        per_feature_psi: Dict[str, float] = {}
        total_psi = 0.0

        for f_idx in range(FEATURE_DIM):
            col = recent_matrix[:, f_idx]
            edges = baseline.bin_edges[f_idx]
            counts, _ = np.histogram(col, bins=edges)
            actual_proportions = counts / max(len(col), 1)
            actual_proportions = np.maximum(actual_proportions, PSI_EPSILON)
            psi = compute_psi(baseline.bin_proportions[f_idx], actual_proportions)
            per_feature_psi[FEATURE_NAMES[f_idx]] = round(psi, 6)
            total_psi += psi

        result = {"total_psi": round(total_psi, 6), **per_feature_psi}
        self._last_psi[tier] = result
        self._drift_alerts[tier] = total_psi > self._psi_threshold
        return result

    def get_status(self) -> Dict[str, object]:
        status: Dict[str, object] = {
            "psiThreshold": self._psi_threshold,
            "windowSize": self._window_size,
        }
        per_tier: Dict[str, object] = {}
        for tier in (1, 2, 3):
            has_baseline = tier in self._baselines
            tier_status: Dict[str, object] = {
                "hasBaseline": has_baseline,
                "baselineSamples": self._baselines[tier].sample_count if has_baseline else 0,
                "recentObservations": self._total_observations[tier],
                "currentWindowSize": len(self._recent_features[tier]),
                "driftDetected": self._drift_alerts[tier],
            }
            if tier in self._last_psi:
                tier_status["psi"] = self._last_psi[tier]
            per_tier[f"tier{tier}"] = tier_status
        status["tiers"] = per_tier
        return status
