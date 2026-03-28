"""Tests for Phase 4E — Champion/challenger shadow scoring."""

from __future__ import annotations

from pathlib import Path
from unittest.mock import MagicMock, patch

import numpy as np
import pytest

from app.kafka.consumer import ShadowStats
from app.models.schemas import MlDetectionResult, ShadowComparisonStats
from app.serving.engine import InferenceEngine


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _export_dummy_autoencoder(path: Path) -> None:
    from app.models.autoencoder import ThreatAutoencoder
    from app.training.trainer import export_onnx

    model = ThreatAutoencoder(input_dim=12, latent_dim=6)
    model.eval()
    export_onnx(model, path)


def _export_dummy_bigru(path: Path, optimal_alpha: float | None = None) -> None:
    from app.models.bigru import ThreatBiGRU
    from app.training.bigru_trainer import export_bigru_onnx

    model = ThreatBiGRU(input_dim=12, hidden_size=16, num_layers=1, dropout=0.0)
    model.eval()
    export_bigru_onnx(model, path, max_seq_len=32, optimal_alpha=optimal_alpha)


# ---------------------------------------------------------------------------
# ShadowStats
# ---------------------------------------------------------------------------

class TestShadowStats:
    def test_empty_stats(self):
        stats = ShadowStats()
        result = stats.get_stats()
        assert result["totalComparisons"] == 0

    def test_record_and_get_stats(self):
        stats = ShadowStats()
        stats.record(tier=1, champion_score=0.5, challenger_score=0.4,
                     champion_weight=1.2, challenger_weight=1.1)
        stats.record(tier=1, champion_score=0.6, challenger_score=0.3,
                     champion_weight=1.4, challenger_weight=1.0)

        result = stats.get_stats()
        assert result["totalComparisons"] == 2
        assert result["meanChampionScore"] == pytest.approx(0.55)
        assert result["meanChallengerScore"] == pytest.approx(0.35)
        assert "challengerBetterCount" in result
        assert "challengerBetterPct" in result

    def test_per_tier_stats(self):
        stats = ShadowStats()
        stats.record(1, 0.5, 0.3, 1.2, 1.0)
        stats.record(2, 0.7, 0.6, 1.5, 1.3)
        stats.record(1, 0.4, 0.2, 1.1, 0.9)

        result = stats.get_stats()
        assert "perTier" in result
        assert "tier1" in result["perTier"]
        assert result["perTier"]["tier1"]["count"] == 2
        assert "tier2" in result["perTier"]
        assert result["perTier"]["tier2"]["count"] == 1

    def test_challenger_better_counting(self):
        stats = ShadowStats()
        # Challenger is "better" when challenger_score < champion_score (lower anomaly = better detection)
        stats.record(1, 0.8, 0.3, 1.5, 1.0)  # challenger better
        stats.record(1, 0.5, 0.7, 1.2, 1.4)  # champion better
        stats.record(1, 0.6, 0.2, 1.3, 0.9)  # challenger better

        result = stats.get_stats()
        assert result["challengerBetterCount"] == 2
        assert result["challengerBetterPct"] == pytest.approx(66.67, abs=0.1)

    def test_max_entries_limit(self):
        stats = ShadowStats(max_entries=5)
        for i in range(10):
            stats.record(1, float(i) / 10, float(i + 1) / 10, 1.0, 1.0)

        # Deques are capped at 5
        result = stats.get_stats()
        assert result["totalComparisons"] == 10  # Total still counts all
        # But mean should reflect only last 5 entries
        assert len(stats._champion_scores) == 5


# ---------------------------------------------------------------------------
# Challenger model loading
# ---------------------------------------------------------------------------

class TestChallengerLoading:
    def test_load_challenger_models(self, tmp_path: Path):
        champion_dir = tmp_path / "champion"
        champion_dir.mkdir()
        challenger_dir = tmp_path / "challenger"
        challenger_dir.mkdir()

        _export_dummy_autoencoder(champion_dir / "autoencoder_v1_tier1.onnx")
        _export_dummy_autoencoder(challenger_dir / "autoencoder_v1_tier1.onnx")
        _export_dummy_bigru(challenger_dir / "bigru_v1_tier1.onnx", optimal_alpha=0.55)

        engine = InferenceEngine(model_dir=str(champion_dir))
        engine.load()

        result = engine.load_challenger(str(challenger_dir))
        assert result["status"] == "ok"
        assert result["modelsLoaded"] == 2  # 1 AE + 1 BiGRU
        assert engine.is_challenger_loaded(1) is True
        assert engine.is_challenger_loaded(2) is False

    def test_challenger_alpha(self, tmp_path: Path):
        challenger_dir = tmp_path / "challenger"
        challenger_dir.mkdir()
        _export_dummy_bigru(challenger_dir / "bigru_v1_tier1.onnx", optimal_alpha=0.45)

        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()
        engine.load_challenger(str(challenger_dir))

        assert engine.get_challenger_alpha(1) == pytest.approx(0.45)
        assert engine.get_challenger_alpha(2) == pytest.approx(0.6)  # default

    def test_challenger_not_loaded_returns_false(self, tmp_path: Path):
        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()
        assert engine.is_challenger_loaded() is False
        assert engine.is_challenger_loaded(1) is False


# ---------------------------------------------------------------------------
# Challenger inference
# ---------------------------------------------------------------------------

class TestChallengerInference:
    def test_predict_challenger_returns_result(self, tmp_path: Path):
        challenger_dir = tmp_path / "challenger"
        challenger_dir.mkdir()
        _export_dummy_autoencoder(challenger_dir / "autoencoder_v1_tier1.onnx")

        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()
        engine.load_challenger(str(challenger_dir))

        mock_output = np.random.rand(1, 12).astype(np.float32)
        mock_session = MagicMock()
        mock_session.get_inputs.return_value = [MagicMock(name="input")]
        mock_session.run.return_value = [mock_output]
        engine._challenger_sessions[1] = mock_session

        features = np.random.rand(12).astype(np.float32)
        result = engine.predict_challenger(features, tier=1)
        assert result is not None
        reconstruction, threshold = result
        assert reconstruction.shape[-1] == 12

    def test_predict_challenger_returns_none_when_not_loaded(self, tmp_path: Path):
        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()

        features = np.random.rand(12).astype(np.float32)
        result = engine.predict_challenger(features, tier=1)
        assert result is None

    def test_predict_challenger_bigru(self, tmp_path: Path):
        mock_output_pred = np.array([[0.65]], dtype=np.float32)
        mock_output_attn = np.random.rand(1, 32).astype(np.float32)
        mock_session = MagicMock()
        mock_input_feat = MagicMock()
        mock_input_feat.name = "features"
        mock_input_mask = MagicMock()
        mock_input_mask.name = "mask"
        mock_session.get_inputs.return_value = [mock_input_feat, mock_input_mask]
        mock_session.run.return_value = [mock_output_pred, mock_output_attn]

        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()
        engine._challenger_bigru_sessions[1] = mock_session

        features_seq = np.random.rand(1, 32, 12).astype(np.float32)
        mask = np.ones((1, 32), dtype=np.float32)
        result = engine.predict_challenger_bigru(features_seq, mask, tier=1)
        assert result is not None
        assert isinstance(result, float)
        assert result == pytest.approx(0.65)


# ---------------------------------------------------------------------------
# MlDetectionResult schema with challenger fields
# ---------------------------------------------------------------------------

class TestMlDetectionResultChallenger:
    def test_challenger_fields_optional(self):
        result = MlDetectionResult(
            customerId="cust-1",
            attackMac="aa:bb:cc:dd:ee:ff",
            attackIp="10.0.0.1",
            tier=1,
            windowStart="2026-01-01T00:00:00Z",
            windowEnd="2026-01-01T00:05:00Z",
            mlScore=0.5,
            mlWeight=1.2,
            mlConfidence=0.5,
            anomalyType="borderline",
            reconstructionError=0.1,
            threshold=0.3,
            modelVersion="autoencoder_v1_tier1",
        )
        assert result.challengerScore is None
        assert result.challengerWeight is None
        assert result.challengerVersion is None

    def test_challenger_fields_populated(self):
        result = MlDetectionResult(
            customerId="cust-1",
            attackMac="aa:bb:cc:dd:ee:ff",
            attackIp="10.0.0.1",
            tier=1,
            windowStart="2026-01-01T00:00:00Z",
            windowEnd="2026-01-01T00:05:00Z",
            mlScore=0.5,
            mlWeight=1.2,
            mlConfidence=0.5,
            anomalyType="borderline",
            reconstructionError=0.1,
            threshold=0.3,
            modelVersion="autoencoder_v1_tier1",
            challengerScore=0.4,
            challengerWeight=1.1,
            challengerVersion="challenger_tier1",
        )
        assert result.challengerScore == 0.4
        assert result.challengerWeight == 1.1
        assert result.challengerVersion == "challenger_tier1"


# ---------------------------------------------------------------------------
# ShadowComparisonStats schema
# ---------------------------------------------------------------------------

class TestShadowComparisonStatsSchema:
    def test_default_values(self):
        s = ShadowComparisonStats()
        assert s.totalComparisons == 0
        assert s.meanChampionScore == 0.0
        assert s.challengerBetterPct == 0.0

    def test_populated(self):
        s = ShadowComparisonStats(
            totalComparisons=100,
            meanChampionScore=0.55,
            meanChallengerScore=0.45,
            meanScoreDelta=-0.1,
            meanAbsScoreDelta=0.1,
            meanChampionWeight=1.3,
            meanChallengerWeight=1.1,
            challengerBetterCount=60,
            challengerBetterPct=60.0,
        )
        assert s.totalComparisons == 100
        assert s.challengerBetterPct == 60.0
