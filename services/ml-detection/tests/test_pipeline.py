"""Tests for Phase 4B — Unified training pipeline."""

from __future__ import annotations

from pathlib import Path
from unittest.mock import MagicMock, patch

import numpy as np
import pytest

from app.training.pipeline import TrainingPipeline, FEATURE_DIM


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _synthetic_features(n: int = 256) -> np.ndarray:
    rng = np.random.RandomState(42)
    return rng.rand(n, FEATURE_DIM).astype(np.float32)


# ---------------------------------------------------------------------------
# Step: Extract
# ---------------------------------------------------------------------------

class TestStepExtract:
    def test_extract_from_csv(self, tmp_path: Path):
        features = _synthetic_features(128)
        csv_path = tmp_path / "features.csv"
        np.savetxt(csv_path, features, delimiter=",")

        pipeline = TrainingPipeline(tier=1, model_dir=str(tmp_path), csv_path=str(csv_path))
        result = pipeline._step_extract()

        assert result.shape == (128, FEATURE_DIM)
        assert pipeline._metrics["training_samples"] == 128

    def test_extract_fallback_synthetic(self, tmp_path: Path):
        """When no CSV and postgres returns empty, falls back to synthetic data."""
        with patch("app.training.pipeline.load_from_postgres", return_value=np.zeros((0, 12), dtype=np.float32)):
            pipeline = TrainingPipeline(tier=1, model_dir=str(tmp_path))
            result = pipeline._step_extract()

        assert result.shape == (256, FEATURE_DIM)
        assert pipeline._metrics["training_samples"] == 256


# ---------------------------------------------------------------------------
# Step: Train Autoencoder
# ---------------------------------------------------------------------------

class TestStepTrainAutoencoder:
    def test_produces_onnx(self, tmp_path: Path):
        features = _synthetic_features(100)
        pipeline = TrainingPipeline(tier=2, model_dir=str(tmp_path), ae_epochs=2)

        model, ae_path = pipeline._step_train_autoencoder(features)
        assert ae_path.exists()
        assert str(ae_path).endswith("autoencoder_v1_tier2.onnx")
        assert pipeline._metrics["autoencoder_epochs"] == 2


# ---------------------------------------------------------------------------
# Step: Generate BiGRU Labels
# ---------------------------------------------------------------------------

class TestStepGenerateBigruLabels:
    def test_produces_sequences(self, tmp_path: Path):
        from app.training.trainer import train_autoencoder

        features = _synthetic_features(200)
        ae_model = train_autoencoder(features, epochs=2)

        pipeline = TrainingPipeline(tier=1, model_dir=str(tmp_path))
        sequences, masks, labels, groups = pipeline._step_generate_bigru_labels(features, ae_model)

        assert sequences.ndim == 3
        assert sequences.shape[2] == FEATURE_DIM
        assert masks.shape[0] == sequences.shape[0]
        assert labels.shape[0] == sequences.shape[0]
        assert groups.shape[0] == sequences.shape[0]
        assert pipeline._metrics["bigru_training_sequences"] == len(sequences)


# ---------------------------------------------------------------------------
# Step: Train BiGRU (skip when insufficient data)
# ---------------------------------------------------------------------------

class TestStepTrainBigru:
    def test_skip_when_insufficient(self, tmp_path: Path):
        """< 20 sequences → skips BiGRU training."""
        pipeline = TrainingPipeline(tier=1, model_dir=str(tmp_path), bigru_epochs=2)
        sequences = np.random.rand(10, 32, FEATURE_DIM).astype(np.float32)
        masks = np.ones((10, 32), dtype=np.float32)
        labels = np.random.rand(10).astype(np.float32)
        groups = np.arange(10, dtype=np.int64)

        path, alpha = pipeline._step_train_bigru(sequences, masks, labels, groups)
        assert pipeline._metrics.get("bigru_skipped") is True
        assert alpha is None


# ---------------------------------------------------------------------------
# Full pipeline (end-to-end, with CSV shortcut)
# ---------------------------------------------------------------------------

class TestFullPipeline:
    def test_end_to_end(self, tmp_path: Path):
        """Full pipeline from CSV → AE → BiGRU → ONNX export."""
        features = _synthetic_features(300)
        csv_path = tmp_path / "features.csv"
        np.savetxt(csv_path, features, delimiter=",")

        pipeline = TrainingPipeline(
            tier=1,
            model_dir=str(tmp_path),
            ae_epochs=2,
            bigru_epochs=2,
            csv_path=str(csv_path),
            do_optimize_alpha=True,
            alpha_candidates=[0.4, 0.6],
        )
        metrics = pipeline.run()

        assert "training_samples" in metrics
        assert "autoencoder_path" in metrics
        assert "bigru_path" in metrics
        assert "total_elapsed_seconds" in metrics
        assert metrics["tier"] == 1

        ae_path = Path(metrics["autoencoder_path"])
        bigru_path = Path(metrics["bigru_path"])
        assert ae_path.exists()
        assert bigru_path.exists()

    def test_max_seq_len_per_tier(self, tmp_path: Path):
        """Verify max_seq_len uses tier-specific values."""
        p1 = TrainingPipeline(tier=1, model_dir=str(tmp_path))
        p2 = TrainingPipeline(tier=2, model_dir=str(tmp_path))
        p3 = TrainingPipeline(tier=3, model_dir=str(tmp_path))
        assert p1.max_seq_len == 32
        assert p2.max_seq_len == 32
        assert p3.max_seq_len == 48
