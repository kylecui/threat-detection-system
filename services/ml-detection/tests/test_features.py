import math

import numpy as np
import pytest

from app.features.extractor import FeatureExtractor
from app.models.schemas import AggregatedAttackData


def test_feature_extraction_vector_dim(sample_attack_data: AggregatedAttackData) -> None:
    extractor = FeatureExtractor()
    vector = extractor.extract(sample_attack_data)
    assert vector.shape == (12,)


def test_tier_stratification_separate_stats(sample_attack_data: AggregatedAttackData) -> None:
    extractor = FeatureExtractor()

    tier1_first = extractor.extract(sample_attack_data)
    tier1_second_input = sample_attack_data.model_copy(update={"attackCount": sample_attack_data.attackCount + 20})
    tier1_second = extractor.extract(tier1_second_input)

    tier2_data = sample_attack_data.model_copy(update={"tier": 2})
    tier2_first = extractor.extract(tier2_data)

    np.testing.assert_allclose(tier1_first, tier2_first)
    assert not np.allclose(tier1_second, tier2_first)


def test_log_transforms(sample_attack_data: AggregatedAttackData) -> None:
    extractor = FeatureExtractor()
    raw = extractor.build_raw_features(sample_attack_data)

    assert raw[0] == np.float32(math.log1p(sample_attack_data.attackCount))
    assert raw[5] == np.float32(math.log1p(sample_attack_data.netWeight))
    assert raw[7] == np.float32(math.log1p(sample_attack_data.eventTimeSpan / 1000.0))


def test_cyclical_hour_encoding() -> None:
    extractor = FeatureExtractor()
    payload = AggregatedAttackData(
        customerId="cust-a",
        attackMac="11:22:33:44:55:66",
        attackCount=1,
        uniqueIps=1,
        uniquePorts=1,
        tier=1,
        windowStart="2026-03-27T06:00:00Z",
    )
    raw = extractor.build_raw_features(payload)
    angle = 2.0 * math.pi * (6 / 24.0)
    assert raw[10] == np.float32(math.sin(angle))
    assert raw[11] == np.float32(math.cos(angle))


def test_per_customer_normalization_cold_start_raw(sample_attack_data: AggregatedAttackData) -> None:
    extractor = FeatureExtractor()
    raw = extractor.build_raw_features(sample_attack_data)
    first = extractor.extract(sample_attack_data)
    np.testing.assert_allclose(first, raw)


def test_missing_optional_fields_defaults_work() -> None:
    extractor = FeatureExtractor()
    payload = AggregatedAttackData(
        customerId="cust-b",
        attackMac="aa:aa:aa:aa:aa:aa",
        attackCount=5,
        uniqueIps=2,
        uniquePorts=3,
        tier=3,
    )
    vector = extractor.extract(payload)
    assert vector.shape == (12,)
    assert float(vector[3]) == pytest.approx(0.1)
