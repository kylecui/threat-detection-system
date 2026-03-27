import numpy as np

from app.config import settings
from app.serving.engine import InferenceEngine
from app.serving.scorer import anomaly_type, score_to_weight


def test_fallback_when_model_not_loaded(sample_feature_vector: np.ndarray) -> None:
    engine = InferenceEngine(model_dir="/tmp/does-not-exist")
    engine.load()

    reconstruction, threshold = engine.predict(sample_feature_vector, tier=1)
    np.testing.assert_allclose(reconstruction, sample_feature_vector)
    assert threshold == settings.ml_confidence_threshold or threshold == 0.3


def test_scorer_mapping_covers_ranges() -> None:
    assert 0.8 <= score_to_weight(0.1, 1.0) <= 1.0
    assert score_to_weight(0.4, 1.0) == 1.0
    assert 1.0 <= score_to_weight(0.6, 1.0) <= 1.5
    assert 1.5 <= score_to_weight(0.8, 1.0) <= 2.5
    assert 2.5 <= score_to_weight(0.95, 1.0) <= 3.0


def test_scorer_low_confidence_dampens_toward_neutral() -> None:
    high_conf = score_to_weight(0.9, 1.0)
    low_conf = score_to_weight(0.9, 0.1)
    assert abs(low_conf - 1.0) < abs(high_conf - 1.0)


def test_anomaly_type_classification() -> None:
    assert anomaly_type(0.1) == "normal"
    assert anomaly_type(0.5) == "borderline"
    assert anomaly_type(0.8) == "statistical_outlier"
