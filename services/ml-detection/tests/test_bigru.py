from __future__ import annotations

import tempfile
from pathlib import Path

import numpy as np
import onnxruntime as ort
import pytest
import torch

from app.models.bigru import ThreatBiGRU


def test_forward_output_shape() -> None:
    model = ThreatBiGRU(input_dim=12, hidden_size=64)
    model.eval()
    x = torch.randn(4, 32, 12)
    mask = torch.ones(4, 32)
    pred, attn = model(x, mask)
    assert pred.shape == (4, 1)
    assert attn.shape == (4, 32)


def test_all_padded_mask_no_nan() -> None:
    model = ThreatBiGRU(input_dim=12, hidden_size=64)
    model.eval()
    x = torch.randn(2, 16, 12)
    mask = torch.zeros(2, 16)
    pred, attn = model(x, mask)
    assert not torch.isnan(pred).any()
    assert not torch.isnan(attn).any()


def test_single_timestep() -> None:
    model = ThreatBiGRU(input_dim=12, hidden_size=64)
    model.eval()
    x = torch.randn(1, 1, 12)
    mask = torch.ones(1, 1)
    pred, attn = model(x, mask)
    assert pred.shape == (1, 1)
    assert 0.0 <= pred.item() <= 1.0


def test_attention_weights_sum_to_one() -> None:
    model = ThreatBiGRU(input_dim=12, hidden_size=64)
    model.eval()
    x = torch.randn(3, 10, 12)
    mask = torch.zeros(3, 10)
    mask[0, :5] = 1.0
    mask[1, :8] = 1.0
    mask[2, :10] = 1.0
    _, attn = model(x, mask)
    for i in range(3):
        valid_sum = attn[i].sum().item()
        assert abs(valid_sum - 1.0) < 1e-4, f"Attention weights sum={valid_sum}"


def test_prediction_range() -> None:
    model = ThreatBiGRU(input_dim=12, hidden_size=64)
    model.eval()
    x = torch.randn(8, 20, 12)
    mask = torch.ones(8, 20)
    pred, _ = model(x, mask)
    assert (pred >= 0.0).all()
    assert (pred <= 1.0).all()


def test_onnx_export_roundtrip() -> None:
    model = ThreatBiGRU(input_dim=12, hidden_size=64)
    model.eval()

    x = torch.randn(2, 16, 12)
    mask = torch.ones(2, 16)

    with torch.no_grad():
        torch_pred, _ = model(x, mask)

    with tempfile.TemporaryDirectory() as tmpdir:
        onnx_path = str(Path(tmpdir) / "bigru_test.onnx")
        torch.onnx.export(
            model,
            (x, mask),
            onnx_path,
            opset_version=17,
            input_names=["features", "mask"],
            output_names=["prediction", "attn_weights"],
            dynamic_axes={
                "features": {0: "batch", 1: "seq_len"},
                "mask": {0: "batch", 1: "seq_len"},
                "prediction": {0: "batch"},
                "attn_weights": {0: "batch", 1: "seq_len"},
            },
        )

        session = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])
        onnx_out = session.run(
            ["prediction"],
            {"features": x.numpy(), "mask": mask.numpy()},
        )

    np.testing.assert_allclose(
        torch_pred.numpy(), onnx_out[0], atol=1e-4,
    )


def test_onnx_dynamic_seq_len() -> None:
    model = ThreatBiGRU(input_dim=12, hidden_size=64)
    model.eval()

    with tempfile.TemporaryDirectory() as tmpdir:
        onnx_path = str(Path(tmpdir) / "bigru_dyn.onnx")
        dummy_x = torch.randn(1, 8, 12)
        dummy_mask = torch.ones(1, 8)
        torch.onnx.export(
            model,
            (dummy_x, dummy_mask),
            onnx_path,
            opset_version=17,
            input_names=["features", "mask"],
            output_names=["prediction", "attn_weights"],
            dynamic_axes={
                "features": {0: "batch", 1: "seq_len"},
                "mask": {0: "batch", 1: "seq_len"},
                "prediction": {0: "batch"},
                "attn_weights": {0: "batch", 1: "seq_len"},
            },
        )

        session = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])

        # Run with different sequence lengths
        for seq_len in (4, 16, 32):
            x = np.random.randn(1, seq_len, 12).astype(np.float32)
            m = np.ones((1, seq_len), dtype=np.float32)
            out = session.run(["prediction"], {"features": x, "mask": m})
            assert out[0].shape == (1, 1)
