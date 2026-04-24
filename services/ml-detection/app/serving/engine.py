from __future__ import annotations

import logging
import threading
from pathlib import Path
from typing import Dict, Optional, Tuple

import numpy as np
import onnxruntime as ort

logger = logging.getLogger(__name__)


class InferenceEngine:
    def __init__(self, model_dir: str, default_threshold: float = 0.3) -> None:
        self.model_dir = Path(model_dir)
        self.default_threshold = default_threshold
        self._sessions: Dict[int, ort.InferenceSession] = {}
        self._thresholds: Dict[int, float] = {
            1: default_threshold,
            2: default_threshold,
            3: default_threshold,
        }
        self._model_paths: Dict[int, Path] = {
            1: self.model_dir / "autoencoder_v1_tier1.onnx",
            2: self.model_dir / "autoencoder_v1_tier2.onnx",
            3: self.model_dir / "autoencoder_v1_tier3.onnx",
        }
        self._bigru_sessions: Dict[int, ort.InferenceSession] = {}
        self._bigru_model_paths: Dict[int, Path] = {
            1: self.model_dir / "bigru_v1_tier1.onnx",
            2: self.model_dir / "bigru_v1_tier2.onnx",
            3: self.model_dir / "bigru_v1_tier3.onnx",
        }
        self._optimal_alphas: Dict[int, float] = {}
        self._file_mtimes: Dict[str, float] = {}
        self._reload_lock = threading.Lock()
        self._reload_count: int = 0

        self._challenger_sessions: Dict[int, ort.InferenceSession] = {}
        self._challenger_bigru_sessions: Dict[int, ort.InferenceSession] = {}
        self._challenger_thresholds: Dict[int, float] = {}
        self._challenger_optimal_alphas: Dict[int, float] = {}
        self._challenger_dir: Optional[Path] = None

    def load(self) -> None:
        providers = ["CPUExecutionProvider"]
        sess_options = ort.SessionOptions()
        for tier, path in self._model_paths.items():
            if not path.exists():
                logger.warning(
                    "Autoencoder model file not found, skipping tier %d: %s", tier, path
                )
                continue
            session = ort.InferenceSession(
                str(path), sess_options=sess_options, providers=providers
            )
            self._sessions[tier] = session
            self._file_mtimes[str(path)] = path.stat().st_mtime

            metadata_map = session.get_modelmeta().custom_metadata_map
            threshold_str = metadata_map.get("threshold")
            if threshold_str:
                try:
                    self._thresholds[tier] = float(threshold_str)
                except ValueError:
                    self._thresholds[tier] = self.default_threshold

        self._load_bigru(sess_options, providers)

    def _load_bigru(self, sess_options: ort.SessionOptions, providers: list) -> None:
        for tier, path in self._bigru_model_paths.items():
            if not path.exists():
                logger.warning(
                    "BiGRU model file not found, skipping tier %d: %s", tier, path
                )
                continue
            session = ort.InferenceSession(
                str(path), sess_options=sess_options, providers=providers
            )
            self._bigru_sessions[tier] = session
            self._file_mtimes[str(path)] = path.stat().st_mtime

            metadata_map = session.get_modelmeta().custom_metadata_map
            alpha_str = metadata_map.get("optimal_alpha")
            if alpha_str:
                try:
                    self._optimal_alphas[tier] = float(alpha_str)
                except ValueError:
                    pass

    def reload(self) -> Dict[str, object]:
        """Reload all models from disk. Thread-safe via lock."""
        with self._reload_lock:
            old_sessions = dict(self._sessions)
            old_bigru = dict(self._bigru_sessions)

            self._sessions.clear()
            self._bigru_sessions.clear()
            self._optimal_alphas.clear()
            self._file_mtimes.clear()

            try:
                self.load()
                self._reload_count += 1
                reloaded = {
                    "status": "ok",
                    "reloadCount": self._reload_count,
                    "modelsLoaded": self.model_info(),
                }
                logger.info(
                    "Model reload #%d successful: %s", self._reload_count, reloaded
                )
                return reloaded
            except Exception as exc:
                logger.error("Reload failed, restoring previous sessions: %s", exc)
                self._sessions = old_sessions
                self._bigru_sessions = old_bigru
                return {"status": "error", "error": str(exc)}

    def check_for_updates(self) -> bool:
        """Check if any ONNX files have been modified since last load. Returns True if reload needed."""
        all_paths = list(self._model_paths.values()) + list(
            self._bigru_model_paths.values()
        )
        for path in all_paths:
            if not path.exists():
                if str(path) in self._file_mtimes:
                    return True
                continue
            current_mtime = path.stat().st_mtime
            stored_mtime = self._file_mtimes.get(str(path))
            if stored_mtime is None or current_mtime > stored_mtime:
                return True
        return False

    def is_model_loaded(self, tier: int | None = None) -> bool:
        if tier is None:
            return bool(self._sessions)
        return tier in self._sessions

    def is_bigru_loaded(self, tier: int | None = None) -> bool:
        if tier is None:
            return bool(self._bigru_sessions)
        return tier in self._bigru_sessions

    def get_optimal_alpha(self, tier: int, default: float = 0.6) -> float:
        return self._optimal_alphas.get(tier, default)

    @property
    def reload_count(self) -> int:
        return self._reload_count

    def model_info(self) -> Dict[str, bool]:
        info: Dict[str, bool] = {}
        for tier in (1, 2, 3):
            info[f"tier{tier}"] = self.is_model_loaded(tier)
            info[f"tier{tier}_bigru"] = self.is_bigru_loaded(tier)
        return info

    def model_metadata(self) -> Dict[int, Dict[str, str | float | bool]]:
        data: Dict[int, Dict[str, str | float | bool]] = {}
        for tier in (1, 2, 3):
            data[tier] = {
                "available": tier in self._sessions,
                "threshold": self._thresholds[tier],
                "modelPath": str(self._model_paths[tier]),
                "bigruAvailable": tier in self._bigru_sessions,
                "bigruModelPath": str(self._bigru_model_paths[tier]),
                "optimalAlpha": self._optimal_alphas.get(tier, 0.6),
            }
        return data

    def predict(self, features: np.ndarray, tier: int) -> Tuple[np.ndarray, float]:
        if tier not in self._sessions:
            return features.astype(np.float32), self._thresholds.get(
                tier, self.default_threshold
            )

        session = self._sessions[tier]
        input_name = session.get_inputs()[0].name
        arr = np.asarray(features, dtype=np.float32)
        if arr.ndim == 1:
            arr = np.expand_dims(arr, axis=0)

        output = session.run(None, {input_name: arr})[0]
        return np.asarray(output, dtype=np.float32), self._thresholds[tier]

    def predict_bigru(
        self, features_seq: np.ndarray, mask: np.ndarray, tier: int
    ) -> Optional[float]:
        if tier not in self._bigru_sessions:
            return None

        session = self._bigru_sessions[tier]
        input_names = [inp.name for inp in session.get_inputs()]
        feeds = {
            input_names[0]: np.asarray(features_seq, dtype=np.float32),
            input_names[1]: np.asarray(mask, dtype=np.float32),
        }
        outputs = session.run(None, feeds)
        prediction = float(outputs[0].flatten()[0])
        return prediction

    def load_challenger(self, challenger_dir: str) -> Dict[str, object]:
        """Load challenger models from a separate directory for shadow scoring."""
        self._challenger_dir = Path(challenger_dir)
        self._challenger_sessions.clear()
        self._challenger_bigru_sessions.clear()
        self._challenger_thresholds.clear()
        self._challenger_optimal_alphas.clear()

        providers = ["CPUExecutionProvider"]
        sess_options = ort.SessionOptions()
        loaded_count = 0

        for tier in (1, 2, 3):
            ae_path = self._challenger_dir / f"autoencoder_v1_tier{tier}.onnx"
            if ae_path.exists():
                session = ort.InferenceSession(
                    str(ae_path), sess_options=sess_options, providers=providers
                )
                self._challenger_sessions[tier] = session
                loaded_count += 1
                metadata_map = session.get_modelmeta().custom_metadata_map
                threshold_str = metadata_map.get("threshold")
                if threshold_str:
                    try:
                        self._challenger_thresholds[tier] = float(threshold_str)
                    except ValueError:
                        pass

            bigru_path = self._challenger_dir / f"bigru_v1_tier{tier}.onnx"
            if bigru_path.exists():
                session = ort.InferenceSession(
                    str(bigru_path), sess_options=sess_options, providers=providers
                )
                self._challenger_bigru_sessions[tier] = session
                loaded_count += 1
                metadata_map = session.get_modelmeta().custom_metadata_map
                alpha_str = metadata_map.get("optimal_alpha")
                if alpha_str:
                    try:
                        self._challenger_optimal_alphas[tier] = float(alpha_str)
                    except ValueError:
                        pass

        logger.info("Loaded %d challenger models from %s", loaded_count, challenger_dir)
        return {
            "status": "ok",
            "challengerDir": challenger_dir,
            "modelsLoaded": loaded_count,
            "tiers": {
                tier: {
                    "autoencoder": tier in self._challenger_sessions,
                    "bigru": tier in self._challenger_bigru_sessions,
                }
                for tier in (1, 2, 3)
            },
        }

    def is_challenger_loaded(self, tier: int | None = None) -> bool:
        if tier is None:
            return bool(self._challenger_sessions)
        return tier in self._challenger_sessions

    def predict_challenger(
        self, features: np.ndarray, tier: int
    ) -> Optional[Tuple[np.ndarray, float]]:
        if tier not in self._challenger_sessions:
            return None

        session = self._challenger_sessions[tier]
        input_name = session.get_inputs()[0].name
        arr = np.asarray(features, dtype=np.float32)
        if arr.ndim == 1:
            arr = np.expand_dims(arr, axis=0)

        output = session.run(None, {input_name: arr})[0]
        threshold = self._challenger_thresholds.get(tier, self.default_threshold)
        return np.asarray(output, dtype=np.float32), threshold

    def predict_challenger_bigru(
        self, features_seq: np.ndarray, mask: np.ndarray, tier: int
    ) -> Optional[float]:
        if tier not in self._challenger_bigru_sessions:
            return None

        session = self._challenger_bigru_sessions[tier]
        input_names = [inp.name for inp in session.get_inputs()]
        feeds = {
            input_names[0]: np.asarray(features_seq, dtype=np.float32),
            input_names[1]: np.asarray(mask, dtype=np.float32),
        }
        outputs = session.run(None, feeds)
        return float(outputs[0].flatten()[0])

    def get_challenger_alpha(self, tier: int, default: float = 0.6) -> float:
        return self._challenger_optimal_alphas.get(tier, default)


_engine_singleton: InferenceEngine | None = None


def get_engine(model_dir: str, default_threshold: float = 0.3) -> InferenceEngine:
    global _engine_singleton
    if _engine_singleton is None:
        _engine_singleton = InferenceEngine(
            model_dir=model_dir, default_threshold=default_threshold
        )
        _engine_singleton.load()
    return _engine_singleton
