import pytest
from pydantic import ValidationError

from app.models.schemas import AggregatedAttackData, MlDetectionResult


def test_aggregated_attack_data_validation_required_fields() -> None:
    payload = AggregatedAttackData(
        customerId="cust-1",
        attackMac="aa:bb:cc:dd:ee:ff",
        attackCount=10,
        uniqueIps=3,
        uniquePorts=2,
        tier=1,
    )
    assert payload.customerId == "cust-1"
    assert payload.tier == 1


def test_ml_detection_result_serialization() -> None:
    result = MlDetectionResult(
        customerId="cust-1",
        attackMac="aa:bb:cc:dd:ee:ff",
        attackIp="10.0.0.1",
        tier=2,
        windowStart="2026-03-27T10:00:00Z",
        windowEnd="2026-03-27T10:05:00Z",
        mlScore=0.72,
        mlWeight=2.1,
        mlConfidence=0.8,
        anomalyType="statistical_outlier",
        reconstructionError=0.45,
        threshold=0.3,
        modelVersion="autoencoder_v1_tier2",
        timestamp="2026-03-27T10:05:00Z",
    )
    dumped = result.model_dump()
    assert dumped["customerId"] == "cust-1"
    assert dumped["mlWeight"] == 2.1


def test_invalid_tier_raises_validation_error() -> None:
    with pytest.raises(ValidationError):
        AggregatedAttackData(
            customerId="cust-1",
            attackMac="aa:bb:cc:dd:ee:ff",
            attackCount=10,
            uniqueIps=3,
            uniquePorts=2,
            tier=4,
        )
