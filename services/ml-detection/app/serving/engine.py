from __future__ import annotations

from pathlib import Path
from typing import Dict, Tuple

import numpy as np
import onnxruntime as ort


class InferenceEngine:
    def __init__(self, model_dir: str, default_threshold: float = 0.3) -> None:
        self.model_dir = Path(model_dir)
        self.default_threshold = default_threshold
        self._sessions: Dict[int, ort.InferenceSession] = {}
        self._thresholds: Dict[int, float] = {1: default_threshold, 2: default_threshold, 3: default_threshold}
        self._model_paths: Dict[int, Path] = {
            1: self.model_dir / "autoencoder_v1_tier1.onnx",
            2: self.model_dir / "autoencoder_v1_tier2.onnx",
            3: self.model_dir / "autoencoder_v1_tier3.onnx",
        }

    def load(self) -> None:
        providers = ["CPUExecutionProvider"]
        sess_options = ort.SessionOptions()
        for tier, path in self._model_paths.items():
            if not path.exists():
                continue
            session = ort.InferenceSession(str(path), sess_options=sess_options, providers=providers)
            self._sessions[tier] = session

            metadata_map = session.get_modelmeta().custom_metadata_map
            threshold_str = metadata_map.get("threshold")
            if threshold_str:
                try:
                    self._thresholds[tier] = float(threshold_str)
                except ValueError:
                    self._thresholds[tier] = self.default_threshold

    def is_model_loaded(self, tier: int | None = None) -> bool:
        if tier is None:
            return bool(self._sessions)
        return tier in self._sessions

    def model_info(self) -> Dict[str, bool]:
        return {f"tier{tier}": self.is_model_loaded(tier) for tier in (1, 2, 3)}

    def model_metadata(self) -> Dict[int, Dict[str, str | float | bool]]:
        data: Dict[int, Dict[str, str | float | bool]] = {}
        for tier in (1, 2, 3):
            data[tier] = {
                "available": tier in self._sessions,
                "threshold": self._thresholds[tier],
                "modelPath": str(self._model_paths[tier]),
            }
        return data

    def predict(self, features: np.ndarray, tier: int) -> Tuple[np.ndarray, float]:
        if tier not in self._sessions:
            return features.astype(np.float32), self._thresholds.get(tier, self.default_threshold)

        session = self._sessions[tier]
        input_name = session.get_inputs()[0].name
        arr = np.asarray(features, dtype=np.float16)
        if arr.ndim == 1:
            arr = np.expand_dims(arr, axis=0)

        output = session.run(None, {input_name: arr})[0]
        return np.asarray(output, dtype=np.float32), self._thresholds[tier]


_engine_singleton: InferenceEngine | None = None


def get_engine(model_dir: str, default_threshold: float = 0.3) -> InferenceEngine:
    global _engine_singleton
    if _engine_singleton is None:
        _engine_singleton = InferenceEngine(model_dir=model_dir, default_threshold=default_threshold)
        _engine_singleton.load()
    return _engine_singleton
