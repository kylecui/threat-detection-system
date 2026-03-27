from datetime import datetime, timezone
from typing import Dict, Literal

from pydantic import BaseModel, Field, field_validator


class AggregatedAttackData(BaseModel):
    customerId: str
    attackMac: str
    attackIp: str = ""
    mostAccessedHoneypotIp: str = ""
    attackCount: int
    uniqueIps: int
    uniquePorts: int
    uniqueDevices: int = 1
    mixedPortWeight: float = 1.0
    netWeight: float = 1.0
    intelScore: int = 0
    intelWeight: float = 1.0
    eventTimeSpan: int = 0
    burstIntensity: float = 0.0
    timeDistributionWeight: float = 1.0
    tier: int
    windowType: str = "TUMBLING"
    windowStart: str = ""
    windowEnd: str = ""
    timestamp: str = ""
    threatScore: float = 0.0
    threatLevel: str = "INFO"

    @field_validator("tier")
    @classmethod
    def validate_tier(cls, value: int) -> int:
        if value not in (1, 2, 3):
            raise ValueError("tier must be 1, 2, or 3")
        return value


class MlDetectionResult(BaseModel):
    customerId: str
    attackMac: str
    attackIp: str
    tier: int
    windowStart: str
    windowEnd: str
    mlScore: float
    mlWeight: float
    mlConfidence: float
    anomalyType: str
    reconstructionError: float
    threshold: float
    modelVersion: str
    timestamp: str = Field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


class HealthResponse(BaseModel):
    status: str
    modelLoaded: bool
    modelsAvailable: Dict[str, bool]
    kafkaConnected: bool


class ModelInfo(BaseModel):
    tier: Literal[1, 2, 3]
    available: bool
    threshold: float
    modelPath: str
