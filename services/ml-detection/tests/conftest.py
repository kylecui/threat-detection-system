import numpy as np
import pytest

from app.models.schemas import AggregatedAttackData


@pytest.fixture
def sample_attack_data() -> AggregatedAttackData:
    return AggregatedAttackData(
        customerId="cust-1",
        attackMac="aa:bb:cc:dd:ee:ff",
        attackIp="10.0.0.2",
        attackCount=120,
        uniqueIps=12,
        uniquePorts=6,
        uniqueDevices=2,
        mixedPortWeight=1.4,
        netWeight=2.0,
        intelScore=40,
        eventTimeSpan=60000,
        burstIntensity=0.7,
        timeDistributionWeight=1.8,
        tier=1,
        windowStart="2026-03-27T10:00:00Z",
        windowEnd="2026-03-27T10:05:00Z",
        timestamp="2026-03-27T10:05:01Z",
    )


@pytest.fixture
def sample_feature_vector() -> np.ndarray:
    return np.array(
        [4.8, 12.0, 6.0, 0.2, 1.4, 1.1, 0.4, 4.1, 0.7, 1.8, 0.5, 0.86],
        dtype=np.float32,
    )
