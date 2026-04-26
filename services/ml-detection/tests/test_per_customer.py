from __future__ import annotations

# pyright: reportAny=none, reportMissingTypeArgument=none, reportUnknownParameterType=none, reportUnknownVariableType=none, reportUnknownMemberType=none, reportPrivateUsage=none, reportUnusedCallResult=none, reportUnknownArgumentType=none

import importlib.util
from pathlib import Path
from unittest.mock import MagicMock, patch

import numpy as np
import pytest

from app.features.extractor import FeatureExtractor
from app.models.schemas import AggregatedAttackData
from app.models.schemas import TrainRequest
from app.serving.engine import InferenceEngine


TORCH_AVAILABLE = importlib.util.find_spec("torch") is not None


def _build_training_features(
    sample_feature_vector: np.ndarray, n: int = 128
) -> np.ndarray:
    rng = np.random.RandomState(42)
    base = np.tile(sample_feature_vector.astype(np.float32), (n, 1))
    noise = rng.normal(0.0, 0.05, size=base.shape).astype(np.float32)
    return base + noise


def _build_pipeline_features(
    sample_feature_vector: np.ndarray,
    sample_attack_data: AggregatedAttackData,
    n: int = 80,
) -> np.ndarray:
    extractor = FeatureExtractor()
    raw = extractor.build_raw_features(sample_attack_data)
    blended = ((raw + sample_feature_vector.astype(np.float32)) / 2.0).astype(
        np.float32
    )
    rng = np.random.RandomState(7)
    base = np.tile(blended, (n, 1))
    jitter = rng.normal(0.0, 0.03, size=base.shape).astype(np.float32)
    return base + jitter


@pytest.mark.skipif(not TORCH_AVAILABLE, reason="torch not installed")
def _export_autoencoder(
    path: Path, sample_feature_vector: np.ndarray, shift: float
) -> None:
    from app.training.trainer import export_onnx, train_autoencoder

    features = _build_training_features(sample_feature_vector + shift, n=160)
    model = train_autoencoder(features, epochs=2)
    export_onnx(model, path)


@pytest.mark.skipif(not TORCH_AVAILABLE, reason="torch not installed")
def _export_bigru(path: Path, optimal_alpha: float | None = None) -> None:
    from app.models.bigru import ThreatBiGRU
    from app.training.bigru_trainer import export_bigru_onnx

    model = ThreatBiGRU(input_dim=12, hidden_size=16, num_layers=1, dropout=0.0)
    model.eval()
    export_bigru_onnx(model, path, max_seq_len=32, optimal_alpha=optimal_alpha)


@pytest.mark.skipif(not TORCH_AVAILABLE, reason="torch not installed")
def test_predict_without_customer_id_uses_global(
    tmp_path: Path, sample_feature_vector: np.ndarray
) -> None:
    _export_autoencoder(
        tmp_path / "global" / "autoencoder_v1_tier1.onnx",
        sample_feature_vector,
        shift=0.0,
    )
    engine = InferenceEngine(model_dir=str(tmp_path))
    engine.load()

    reconstruction, threshold = engine.predict(sample_feature_vector, tier=1)

    assert reconstruction.shape == (1, 12)
    assert threshold == pytest.approx(engine.default_threshold)
    assert not np.allclose(reconstruction.squeeze(0), sample_feature_vector)


@pytest.mark.skipif(not TORCH_AVAILABLE, reason="torch not installed")
def test_predict_with_customer_id_falls_back_to_global(
    tmp_path: Path, sample_feature_vector: np.ndarray
) -> None:
    _export_autoencoder(
        tmp_path / "global" / "autoencoder_v1_tier1.onnx",
        sample_feature_vector,
        shift=0.0,
    )
    engine = InferenceEngine(model_dir=str(tmp_path))
    engine.load()

    global_pred, global_threshold = engine.predict(sample_feature_vector, tier=1)
    fallback_pred, fallback_threshold = engine.predict(
        sample_feature_vector, tier=1, customer_id="nonexistent"
    )

    np.testing.assert_allclose(global_pred, fallback_pred)
    assert fallback_threshold == pytest.approx(global_threshold)


@pytest.mark.skipif(not TORCH_AVAILABLE, reason="torch not installed")
def test_predict_with_customer_model_uses_customer_model(
    tmp_path: Path, sample_feature_vector: np.ndarray
) -> None:
    _export_autoencoder(
        tmp_path / "global" / "autoencoder_v1_tier1.onnx",
        sample_feature_vector,
        shift=0.0,
    )
    _export_autoencoder(
        tmp_path / "cust-1" / "autoencoder_v1_tier1.onnx",
        sample_feature_vector,
        shift=2.5,
    )
    engine = InferenceEngine(model_dir=str(tmp_path))
    engine.load()

    global_pred, _ = engine.predict(sample_feature_vector, tier=1)
    customer_pred, _ = engine.predict(
        sample_feature_vector, tier=1, customer_id="cust-1"
    )

    assert not np.allclose(customer_pred, global_pred)
    assert not np.allclose(customer_pred.squeeze(0), sample_feature_vector)


@pytest.mark.skipif(not TORCH_AVAILABLE, reason="torch not installed")
def test_customer_cache_invalidation(
    tmp_path: Path, sample_feature_vector: np.ndarray
) -> None:
    customer_path = tmp_path / "cust-1" / "autoencoder_v1_tier1.onnx"
    _export_autoencoder(customer_path, sample_feature_vector, shift=0.0)

    engine = InferenceEngine(model_dir=str(tmp_path))
    engine.load()
    before, _ = engine.predict(sample_feature_vector, tier=1, customer_id="cust-1")

    _export_autoencoder(customer_path, sample_feature_vector, shift=3.0)
    cached, _ = engine.predict(sample_feature_vector, tier=1, customer_id="cust-1")
    np.testing.assert_allclose(before, cached)

    engine._customer_cache.invalidate("cust-1")
    after, _ = engine.predict(sample_feature_vector, tier=1, customer_id="cust-1")

    assert not np.allclose(before, after)


@pytest.mark.skipif(not TORCH_AVAILABLE, reason="torch not installed")
def test_predict_bigru_with_customer_id_fallback(tmp_path: Path) -> None:
    engine = InferenceEngine(model_dir=str(tmp_path))
    engine.load()

    features_seq = np.random.rand(1, 32, 12).astype(np.float32)
    mask = np.ones((1, 32), dtype=np.float32)

    assert (
        engine.predict_bigru(features_seq, mask, tier=1, customer_id="nonexistent")
        is None
    )

    _export_bigru(tmp_path / "global" / "bigru_v1_tier1.onnx", optimal_alpha=0.5)
    engine.reload()

    global_score = engine.predict_bigru(features_seq, mask, tier=1)
    fallback_score = engine.predict_bigru(
        features_seq, mask, tier=1, customer_id="nonexistent"
    )

    assert global_score is not None
    assert fallback_score == pytest.approx(global_score)


@pytest.mark.skipif(not TORCH_AVAILABLE, reason="torch not installed")
def test_get_optimal_alpha_with_customer_id(tmp_path: Path) -> None:
    _export_bigru(tmp_path / "cust-1" / "bigru_v1_tier1.onnx", optimal_alpha=0.42)
    engine = InferenceEngine(model_dir=str(tmp_path))
    engine.load()

    assert engine.get_optimal_alpha(1, customer_id="cust-1") == pytest.approx(0.42)


@pytest.mark.skipif(not TORCH_AVAILABLE, reason="torch not installed")
def test_pipeline_with_customer_id_outputs_to_customer_subdir(
    tmp_path: Path,
    sample_feature_vector: np.ndarray,
    sample_attack_data: AggregatedAttackData,
) -> None:
    from app.training.pipeline import TrainingPipeline

    features = _build_pipeline_features(sample_feature_vector, sample_attack_data, n=80)
    pipeline = TrainingPipeline(
        tier=1,
        model_dir=str(tmp_path),
        ae_epochs=1,
        customer_id="cust-1",
    )

    _, ae_path = pipeline._step_train_autoencoder(features)

    assert ae_path.parent == tmp_path / "cust-1"
    assert ae_path.exists()


@pytest.mark.skipif(not TORCH_AVAILABLE, reason="torch not installed")
def test_pipeline_without_customer_id_outputs_to_global_subdir(
    tmp_path: Path,
    sample_feature_vector: np.ndarray,
    sample_attack_data: AggregatedAttackData,
) -> None:
    from app.training.pipeline import TrainingPipeline

    features = _build_pipeline_features(sample_feature_vector, sample_attack_data, n=80)
    pipeline = TrainingPipeline(
        tier=1,
        model_dir=str(tmp_path),
        ae_epochs=1,
        customer_id=None,
    )

    _, ae_path = pipeline._step_train_autoencoder(features)

    assert ae_path.parent == tmp_path / "global"
    assert ae_path.exists()


@pytest.mark.skipif(not TORCH_AVAILABLE, reason="torch not installed")
def test_pipeline_metrics_include_customer_id(
    tmp_path: Path, sample_feature_vector: np.ndarray
) -> None:
    from app.training.pipeline import TrainingPipeline

    csv_path = tmp_path / "features.csv"
    np.savetxt(
        csv_path, _build_training_features(sample_feature_vector, n=12), delimiter=","
    )

    pipeline = TrainingPipeline(
        tier=1,
        model_dir=str(tmp_path),
        ae_epochs=1,
        bigru_epochs=1,
        csv_path=str(csv_path),
        do_optimize_alpha=False,
        customer_id="cust-1",
    )
    metrics = pipeline.run()

    assert "customerId" in metrics
    assert metrics["customerId"] == "cust-1"


def test_train_request_with_customer_id() -> None:
    req = TrainRequest(customerId="cust-1")
    assert req.customerId == "cust-1"


def test_train_request_without_customer_id() -> None:
    req = TrainRequest()
    assert req.customerId is None


@pytest.mark.skipif(not TORCH_AVAILABLE, reason="torch not installed")
def test_load_from_postgres_signature() -> None:
    import importlib

    trainer_module = importlib.import_module("app.training.trainer")

    mock_conn = MagicMock()
    mock_cursor = MagicMock()
    mock_conn.cursor.return_value.__enter__.return_value = mock_cursor
    mock_cursor.fetchall.return_value = []

    with patch.object(trainer_module.psycopg2, "connect", return_value=mock_conn):
        result = trainer_module.load_from_postgres(
            "postgresql://user:pass@localhost:5432/db", tier=2, customer_id="cust-1"
        )

    execute_call = mock_cursor.execute.call_args
    assert execute_call is not None
    _, params = execute_call[0]
    assert params == (2, "cust-1")
    assert result.shape == (0, 12)
