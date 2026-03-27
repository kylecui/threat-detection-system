from __future__ import annotations

import math
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Dict, Iterable, List, Tuple

import numpy as np

from app.models.schemas import AggregatedAttackData


PER_CUSTOMER_FEATURE_INDICES = (0, 1, 2, 7)


@dataclass
class RollingStats:
    count: np.ndarray = field(default_factory=lambda: np.zeros(12, dtype=np.float64))
    mean: np.ndarray = field(default_factory=lambda: np.zeros(12, dtype=np.float64))
    m2: np.ndarray = field(default_factory=lambda: np.zeros(12, dtype=np.float64))

    def update(self, values: np.ndarray, indices: Iterable[int]) -> None:
        for idx in indices:
            x = float(values[idx])
            self.count[idx] += 1.0
            delta = x - self.mean[idx]
            self.mean[idx] += delta / self.count[idx]
            delta2 = x - self.mean[idx]
            self.m2[idx] += delta * delta2

    def z_score(self, values: np.ndarray, indices: Iterable[int]) -> np.ndarray:
        out = values.copy()
        for idx in indices:
            if self.count[idx] < 2:
                continue
            variance = self.m2[idx] / (self.count[idx] - 1.0)
            std = math.sqrt(max(variance, 1e-9))
            out[idx] = (out[idx] - self.mean[idx]) / std
        return out


class FeatureExtractor:
    def __init__(self) -> None:
        self._stats: Dict[Tuple[str, int], RollingStats] = {}

    @staticmethod
    def _extract_hour(timestamp: str) -> int:
        if not timestamp:
            return datetime.now(timezone.utc).hour
        cleaned = timestamp.replace("Z", "+00:00")
        try:
            return datetime.fromisoformat(cleaned).hour
        except ValueError:
            return datetime.now(timezone.utc).hour

    def build_raw_features(self, payload: AggregatedAttackData) -> np.ndarray:
        hour = self._extract_hour(payload.windowStart or payload.timestamp)
        angle = 2.0 * math.pi * (hour / 24.0)
        unique_devices_norm = max(payload.uniqueDevices, 0) / 10.0

        vector = np.array(
            [
                math.log1p(max(payload.attackCount, 0)),
                float(max(payload.uniqueIps, 0)),
                float(max(payload.uniquePorts, 0)),
                unique_devices_norm,
                float(payload.mixedPortWeight),
                math.log1p(max(payload.netWeight, 0.0)),
                float(payload.intelScore) / 100.0,
                math.log1p(max(payload.eventTimeSpan, 0) / 1000.0),
                float(payload.burstIntensity),
                float(payload.timeDistributionWeight),
                math.sin(angle),
                math.cos(angle),
            ],
            dtype=np.float32,
        )
        return vector

    def extract(self, payload: AggregatedAttackData) -> np.ndarray:
        key = (payload.customerId, payload.tier)
        raw = self.build_raw_features(payload)
        tracker = self._stats.get(key)
        if tracker is None:
            tracker = RollingStats()
            self._stats[key] = tracker
            tracker.update(raw.astype(np.float64), PER_CUSTOMER_FEATURE_INDICES)
            return raw

        normalized = tracker.z_score(raw.astype(np.float64), PER_CUSTOMER_FEATURE_INDICES)
        tracker.update(raw.astype(np.float64), PER_CUSTOMER_FEATURE_INDICES)
        return normalized.astype(np.float32)

    def extract_batch(self, payloads: List[AggregatedAttackData]) -> np.ndarray:
        vectors = [self.extract(item) for item in payloads]
        if not vectors:
            return np.zeros((0, 12), dtype=np.float32)
        return np.stack(vectors).astype(np.float32)

    def stats_available(self, customer_id: str, tier: int) -> bool:
        tracker = self._stats.get((customer_id, tier))
        if not tracker:
            return False
        return bool(np.all(tracker.count[list(PER_CUSTOMER_FEATURE_INDICES)] > 1))
