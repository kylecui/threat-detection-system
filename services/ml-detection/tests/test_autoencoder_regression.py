from __future__ import annotations

import numpy as np

from app.features.extractor import FeatureExtractor
from app.models.schemas import AggregatedAttackData
from app.serving.engine import InferenceEngine
from app.serving.scorer import anomaly_type, reconstruction_to_anomaly_score, score_to_weight


def test_autoencoder_infer_fallback_produces_valid_result(sample_attack_data: AggregatedAttackData) -> None:
    """Regression: autoencoder fallback path returns input features unchanged."""
    engine = InferenceEngine(model_dir="/tmp/no-models-here")
    engine.load()

    extractor = FeatureExtractor()
    features = extractor.extract(sample_attack_data)

    reconstruction, threshold = engine.predict(features, sample_attack_data.tier)
    np.testing.assert_allclose(reconstruction, features, atol=1e-6)
    assert threshold == 0.3


def test_autoencoder_scorer_pipeline_unchanged() -> None:
    """Regression: scorer functions produce expected outputs for known inputs."""
    error = 0.15
    threshold = 0.3
    score = reconstruction_to_anomaly_score(error, threshold)
    assert 0.0 <= score <= 1.0

    weight = score_to_weight(score, confidence=score)
    assert 0.5 <= weight <= 3.0

    atype = anomaly_type(score)
    assert atype in ("normal", "borderline", "statistical_outlier")


def test_autoencoder_feature_extraction_shape(sample_attack_data: AggregatedAttackData) -> None:
    """Regression: feature extraction produces 12-dim vector."""
    extractor = FeatureExtractor()
    features = extractor.extract(sample_attack_data)
    assert features.shape == (12,)
    assert features.dtype == np.float32


def test_autoencoder_weight_boundaries_unchanged() -> None:
    """Regression: score_to_weight boundary values are stable."""
    assert 0.8 <= score_to_weight(0.1, 1.0) <= 1.0
    assert score_to_weight(0.4, 1.0) == 1.0
    assert 1.0 <= score_to_weight(0.6, 1.0) <= 1.5
    assert 1.5 <= score_to_weight(0.8, 1.0) <= 2.5
    assert 2.5 <= score_to_weight(0.95, 1.0) <= 3.0
