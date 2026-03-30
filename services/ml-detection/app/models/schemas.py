from datetime import datetime, timezone
from typing import Dict, List, Literal, Optional

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

    @field_validator("windowStart", "windowEnd", "timestamp", mode="before")
    @classmethod
    def coerce_to_str(cls, value):
        """Flink may send timestamps as float/int — coerce to string."""
        if isinstance(value, (int, float)):
            return str(value)
        return value

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
    sequenceLength: int = 0
    temporalScore: float = 0.0
    ensembleMethod: str = "autoencoder_only"
    ensembleAlpha: float = 0.6
    challengerScore: Optional[float] = None
    challengerWeight: Optional[float] = None
    challengerVersion: Optional[str] = None
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
    bigruAvailable: bool = False
    bigruModelPath: str = ""
    optimalAlpha: float = 0.6


class ReloadResponse(BaseModel):
    status: str
    reloadCount: int = 0
    modelsLoaded: Optional[Dict[str, bool]] = None
    error: Optional[str] = None


class ShadowComparisonStats(BaseModel):
    totalComparisons: int = 0
    meanChampionScore: float = 0.0
    meanChallengerScore: float = 0.0
    meanScoreDelta: float = 0.0
    meanAbsScoreDelta: float = 0.0
    meanChampionWeight: float = 0.0
    meanChallengerWeight: float = 0.0
    challengerBetterCount: int = 0
    challengerBetterPct: float = 0.0
    championModelVersion: str = ""
    challengerModelVersion: str = ""
    perTier: Dict[str, object] = Field(default_factory=dict)
