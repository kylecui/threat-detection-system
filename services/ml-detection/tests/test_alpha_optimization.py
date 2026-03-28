"""Tests for Phase 4A — alpha optimization, _prepare_splits, and ONNX metadata embedding."""

from __future__ import annotations

from pathlib import Path
from unittest.mock import patch

import numpy as np
import pytest
import torch

from app.training.bigru_trainer import (
    _prepare_splits,
    build_sequences_from_features,
    export_bigru_onnx,
    optimize_alpha,
    run_training,
    FEATURE_DIM,
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_training_data(
    n_samples: int = 100,
    max_seq_len: int = 32,
    n_groups: int = 5,
    seed: int = 42,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    """Generate synthetic sequences/masks/labels/groups for tests."""
    rng = np.random.RandomState(seed)
    sequences = rng.randn(n_samples, max_seq_len, FEATURE_DIM).astype(np.float32)
    # Random seq lengths → masks
    seq_lens = rng.randint(4, max_seq_len + 1, size=n_samples)
    masks = np.zeros((n_samples, max_seq_len), dtype=np.float32)
    for i, sl in enumerate(seq_lens):
        masks[i, :sl] = 1.0
    labels = rng.rand(n_samples).astype(np.float32)
    groups = rng.randint(0, n_groups, size=n_samples).astype(np.int64)
    return sequences, masks, labels, groups


# ---------------------------------------------------------------------------
# _prepare_splits
# ---------------------------------------------------------------------------

class TestPrepareSplits:
    def test_output_shapes(self):
        sequences, masks, labels, groups = _make_training_data(100)
        train_data, val_data, train_idx, val_idx = _prepare_splits(
            sequences, masks, labels, groups, val_fraction=0.2
        )
        train_seqs, train_masks, train_labels = train_data
        val_seqs, val_masks, val_labels = val_data

        # Indices cover full dataset without overlap
        assert len(train_idx) + len(val_idx) == 100
        assert len(set(train_idx) & set(val_idx)) == 0

        # Tensor shapes match
        assert train_seqs.shape == (len(train_idx), 32, FEATURE_DIM)
        assert val_seqs.shape == (len(val_idx), 32, FEATURE_DIM)
        assert train_labels.shape == (len(train_idx), 1)
        assert val_labels.shape == (len(val_idx), 1)

    def test_group_integrity(self):
        """All samples from one group should be entirely in train OR val, not split."""
        sequences, masks, labels, groups = _make_training_data(200, n_groups=4)
        _train_data, _val_data, train_idx, val_idx = _prepare_splits(
            sequences, masks, labels, groups, val_fraction=0.25
        )
        train_groups = set(groups[train_idx])
        val_groups = set(groups[val_idx])
        # No group appears in both splits
        assert len(train_groups & val_groups) == 0

    def test_tensors_are_float(self):
        sequences, masks, labels, groups = _make_training_data(50)
        train_data, val_data, _, _ = _prepare_splits(
            sequences, masks, labels, groups
        )
        for tensor in (*train_data, *val_data):
            assert tensor.dtype == torch.float32


# ---------------------------------------------------------------------------
# optimize_alpha
# ---------------------------------------------------------------------------

class TestOptimizeAlpha:
    def test_returns_best_alpha_and_results(self):
        sequences, masks, labels, groups = _make_training_data(100)
        best_alpha, results = optimize_alpha(
            sequences, masks, labels, groups,
            alpha_candidates=[0.3, 0.5, 0.7],
        )
        assert best_alpha in [0.3, 0.5, 0.7]
        assert len(results) == 3
        for alpha, loss in results.items():
            assert isinstance(loss, float)
            assert loss >= 0.0

    def test_best_alpha_has_lowest_proxy_loss(self):
        sequences, masks, labels, groups = _make_training_data(100)
        best_alpha, results = optimize_alpha(
            sequences, masks, labels, groups,
            alpha_candidates=[0.2, 0.4, 0.6, 0.8],
        )
        best_loss = results[best_alpha]
        for loss in results.values():
            assert best_loss <= loss + 1e-9

    def test_default_candidates_when_none(self):
        sequences, masks, labels, groups = _make_training_data(80)
        best_alpha, results = optimize_alpha(
            sequences, masks, labels, groups,
            alpha_candidates=None,
        )
        # Default candidates: [0.3, 0.4, 0.5, 0.6, 0.7, 0.8]
        assert len(results) == 6
        assert best_alpha in results

    def test_single_candidate(self):
        sequences, masks, labels, groups = _make_training_data(60)
        best_alpha, results = optimize_alpha(
            sequences, masks, labels, groups,
            alpha_candidates=[0.5],
        )
        assert best_alpha == 0.5
        assert len(results) == 1


# ---------------------------------------------------------------------------
# export_bigru_onnx with optimal_alpha metadata
# ---------------------------------------------------------------------------

class TestExportBigruOnnxMetadata:
    def test_onnx_metadata_contains_alpha(self, tmp_path: Path):
        from app.models.bigru import ThreatBiGRU

        model = ThreatBiGRU(input_dim=FEATURE_DIM, hidden_size=16, num_layers=1, dropout=0.0)
        model.eval()
        out_path = tmp_path / "test_bigru.onnx"

        export_bigru_onnx(model, out_path, max_seq_len=32, optimal_alpha=0.55)

        import onnx
        onnx_model = onnx.load(str(out_path))
        meta = {p.key: p.value for p in onnx_model.metadata_props}
        assert "optimal_alpha" in meta
        assert float(meta["optimal_alpha"]) == pytest.approx(0.55)

    def test_onnx_without_alpha_has_no_metadata(self, tmp_path: Path):
        from app.models.bigru import ThreatBiGRU

        model = ThreatBiGRU(input_dim=FEATURE_DIM, hidden_size=16, num_layers=1, dropout=0.0)
        model.eval()
        out_path = tmp_path / "test_bigru_no_alpha.onnx"

        export_bigru_onnx(model, out_path, max_seq_len=32, optimal_alpha=None)

        import onnx
        onnx_model = onnx.load(str(out_path))
        meta = {p.key: p.value for p in onnx_model.metadata_props}
        assert "optimal_alpha" not in meta


# ---------------------------------------------------------------------------
# run_training returns Tuple[Path, Optional[float]]
# ---------------------------------------------------------------------------

class TestRunTraining:
    def test_returns_tuple_with_alpha(self, tmp_path: Path):
        """run_training with optimize_alpha_flag=True returns (path, float)."""
        # Create synthetic .npz training data
        sequences, masks, labels, groups = _make_training_data(100, max_seq_len=32)
        npz_path = tmp_path / "tier1_train.npz"
        np.savez(npz_path, sequences=sequences, masks=masks, labels=labels, groups=groups)

        out_path, optimal_alpha = run_training(
            tier=1,
            npz_path=str(npz_path),
            epochs=2,
            model_dir=str(tmp_path),
            hidden_size=16,
            num_layers=1,
            dropout=0.0,
            optimize_alpha_flag=True,
            alpha_candidates=[0.4, 0.6],
        )
        assert out_path.exists()
        assert optimal_alpha is not None
        assert 0.0 < optimal_alpha < 1.0

    def test_returns_tuple_without_alpha(self, tmp_path: Path):
        """run_training without alpha optimization returns (path, None)."""
        sequences, masks, labels, groups = _make_training_data(100, max_seq_len=32)
        npz_path = tmp_path / "tier1_train.npz"
        np.savez(npz_path, sequences=sequences, masks=masks, labels=labels, groups=groups)

        out_path, optimal_alpha = run_training(
            tier=1,
            npz_path=str(npz_path),
            epochs=2,
            model_dir=str(tmp_path),
            hidden_size=16,
            num_layers=1,
            dropout=0.0,
            optimize_alpha_flag=False,
        )
        assert out_path.exists()
        assert optimal_alpha is None

    def test_insufficient_data_raises(self, tmp_path: Path):
        """run_training with < 20 sequences raises ValueError."""
        sequences, masks, labels, groups = _make_training_data(10, max_seq_len=32)
        npz_path = tmp_path / "tier1_small.npz"
        np.savez(npz_path, sequences=sequences, masks=masks, labels=labels, groups=groups)

        with pytest.raises(ValueError, match="Not enough training data"):
            run_training(
                tier=1,
                npz_path=str(npz_path),
                epochs=2,
                model_dir=str(tmp_path),
            )
