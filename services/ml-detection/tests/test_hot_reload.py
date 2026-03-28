"""Tests for Phase 4C — Model hot-reload (engine.reload, check_for_updates, mtime tracking)."""

from __future__ import annotations

import threading
import time
from pathlib import Path
from unittest.mock import MagicMock, patch

import numpy as np
import pytest
import torch

from app.serving.engine import InferenceEngine


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _export_dummy_autoencoder(path: Path) -> None:
    """Export a minimal ONNX autoencoder to the given path."""
    from app.models.autoencoder import ThreatAutoencoder
    from app.training.trainer import export_onnx

    model = ThreatAutoencoder(input_dim=12, latent_dim=6)
    model.eval()
    export_onnx(model, path)


def _export_dummy_bigru(path: Path, optimal_alpha: float | None = None) -> None:
    """Export a minimal ONNX BiGRU to the given path."""
    from app.models.bigru import ThreatBiGRU
    from app.training.bigru_trainer import export_bigru_onnx

    model = ThreatBiGRU(input_dim=12, hidden_size=16, num_layers=1, dropout=0.0)
    model.eval()
    export_bigru_onnx(model, path, max_seq_len=32, optimal_alpha=optimal_alpha)


# ---------------------------------------------------------------------------
# reload()
# ---------------------------------------------------------------------------

class TestReload:
    def test_reload_with_models(self, tmp_path: Path):
        ae_path = tmp_path / "autoencoder_v1_tier1.onnx"
        _export_dummy_autoencoder(ae_path)

        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()
        assert engine.is_model_loaded(1)

        result = engine.reload()
        assert result["status"] == "ok"
        assert result["reloadCount"] == 1
        assert engine.is_model_loaded(1)

    def test_reload_increments_counter(self, tmp_path: Path):
        _export_dummy_autoencoder(tmp_path / "autoencoder_v1_tier1.onnx")

        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()

        engine.reload()
        engine.reload()
        assert engine.reload_count == 2

    def test_reload_restores_on_failure(self, tmp_path: Path):
        _export_dummy_autoencoder(tmp_path / "autoencoder_v1_tier1.onnx")

        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()
        assert engine.is_model_loaded(1)

        # Corrupt the model file so reload fails
        corrupt_path = tmp_path / "autoencoder_v1_tier1.onnx"
        corrupt_path.write_bytes(b"not-a-valid-onnx-model")

        result = engine.reload()
        assert result["status"] == "error"
        # Previous sessions should be restored
        assert engine.is_model_loaded(1)

    def test_reload_empty_dir(self, tmp_path: Path):
        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()
        result = engine.reload()
        assert result["status"] == "ok"


# ---------------------------------------------------------------------------
# check_for_updates()
# ---------------------------------------------------------------------------

class TestCheckForUpdates:
    def test_no_updates_when_unchanged(self, tmp_path: Path):
        _export_dummy_autoencoder(tmp_path / "autoencoder_v1_tier1.onnx")

        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()

        assert engine.check_for_updates() is False

    def test_detects_new_file(self, tmp_path: Path):
        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()

        # Now create a model file that wasn't there before
        _export_dummy_autoencoder(tmp_path / "autoencoder_v1_tier1.onnx")

        assert engine.check_for_updates() is True

    def test_detects_modified_file(self, tmp_path: Path):
        ae_path = tmp_path / "autoencoder_v1_tier1.onnx"
        _export_dummy_autoencoder(ae_path)

        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()

        # Touch the file to update mtime
        time.sleep(0.05)
        ae_path.write_bytes(ae_path.read_bytes())

        assert engine.check_for_updates() is True

    def test_detects_deleted_file(self, tmp_path: Path):
        ae_path = tmp_path / "autoencoder_v1_tier1.onnx"
        _export_dummy_autoencoder(ae_path)

        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()

        # Delete the model
        ae_path.unlink()

        assert engine.check_for_updates() is True


# ---------------------------------------------------------------------------
# Optimal alpha reading from ONNX metadata
# ---------------------------------------------------------------------------

class TestOptimalAlphaFromOnnx:
    def test_reads_alpha_from_bigru_metadata(self, tmp_path: Path):
        _export_dummy_bigru(tmp_path / "bigru_v1_tier2.onnx", optimal_alpha=0.45)

        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()

        assert engine.get_optimal_alpha(2) == pytest.approx(0.45)

    def test_default_alpha_when_no_metadata(self, tmp_path: Path):
        _export_dummy_bigru(tmp_path / "bigru_v1_tier1.onnx", optimal_alpha=None)

        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()

        # No metadata → returns default
        assert engine.get_optimal_alpha(1) == pytest.approx(0.6)

    def test_default_alpha_when_no_model(self, tmp_path: Path):
        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()
        assert engine.get_optimal_alpha(3, default=0.7) == pytest.approx(0.7)


# ---------------------------------------------------------------------------
# Thread safety
# ---------------------------------------------------------------------------

class TestThreadSafety:
    def test_concurrent_reloads_dont_crash(self, tmp_path: Path):
        _export_dummy_autoencoder(tmp_path / "autoencoder_v1_tier1.onnx")

        engine = InferenceEngine(model_dir=str(tmp_path))
        engine.load()

        errors: list[Exception] = []

        def do_reload():
            try:
                engine.reload()
            except Exception as exc:
                errors.append(exc)

        threads = [threading.Thread(target=do_reload) for _ in range(5)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        assert len(errors) == 0
        assert engine.reload_count >= 1
