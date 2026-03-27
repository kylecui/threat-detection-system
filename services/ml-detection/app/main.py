from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Dict, List

import numpy as np
from fastapi import FastAPI

from app.config import settings
from app.features.extractor import FeatureExtractor
from app.kafka.consumer import MlDetectionConsumer
from app.kafka.producer import MlDetectionProducer
from app.models.schemas import AggregatedAttackData, HealthResponse, MlDetectionResult, ModelInfo
from app.serving.engine import InferenceEngine, get_engine
from app.serving.scorer import anomaly_type, reconstruction_to_anomaly_score, score_to_weight


logging.basicConfig(level=getattr(logging, settings.log_level.upper(), logging.INFO))
logger = logging.getLogger(__name__)


class AppState:
    def __init__(self) -> None:
        self.engine: InferenceEngine | None = None
        self.extractor = FeatureExtractor()
        self.producer: MlDetectionProducer | None = None
        self.consumer: MlDetectionConsumer | None = None


state = AppState()


@asynccontextmanager
async def lifespan(_: FastAPI):
    state.engine = get_engine(settings.model_dir, settings.ml_confidence_threshold)
    state.producer = MlDetectionProducer(settings.kafka_bootstrap_servers, settings.kafka_output_topic)
    await state.producer.start()

    state.consumer = MlDetectionConsumer(
        bootstrap_servers=settings.kafka_bootstrap_servers,
        topic=settings.kafka_input_topic,
        group_id=settings.kafka_consumer_group,
        producer=state.producer,
        feature_extractor=state.extractor,
        engine=state.engine,
        default_weight=settings.ml_default_weight,
    )
    await state.consumer.start()

    try:
        yield
    finally:
        if state.consumer:
            await state.consumer.stop()
        if state.producer:
            await state.producer.stop()


app = FastAPI(title="ML Threat Detection Service", version="1.0.0", lifespan=lifespan)


def _run_inference_sync(data: AggregatedAttackData) -> MlDetectionResult:
    if not state.engine:
        raise RuntimeError("Inference engine not initialized")

    features = state.extractor.extract(data)
    reconstruction, threshold = state.engine.predict(features, data.tier)
    reconstruction_one = reconstruction[0] if reconstruction.ndim == 2 else reconstruction
    reconstruction_error = float(np.mean((features - reconstruction_one) ** 2))
    ml_score = reconstruction_to_anomaly_score(reconstruction_error, threshold)
    confidence = min(1.0, max(0.0, ml_score))
    model_loaded = state.engine.is_model_loaded(data.tier)

    if model_loaded:
        ml_weight = score_to_weight(ml_score, confidence)
        version = f"autoencoder_v1_tier{data.tier}"
    else:
        ml_weight = settings.ml_default_weight
        version = "fallback"

    return MlDetectionResult(
        customerId=data.customerId,
        attackMac=data.attackMac,
        attackIp=data.attackIp,
        tier=data.tier,
        windowStart=data.windowStart,
        windowEnd=data.windowEnd,
        mlScore=ml_score,
        mlWeight=ml_weight,
        mlConfidence=confidence,
        anomalyType=anomaly_type(ml_score),
        reconstructionError=reconstruction_error,
        threshold=threshold,
        modelVersion=version,
        timestamp=datetime.now(timezone.utc).isoformat(),
    )


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    model_loaded = bool(state.engine and state.engine.is_model_loaded())
    models_available: Dict[str, bool] = state.engine.model_info() if state.engine else {"tier1": False, "tier2": False, "tier3": False}
    kafka_connected = bool(state.producer and state.producer.connected and state.consumer and state.consumer.connected)
    return HealthResponse(
        status="ok",
        modelLoaded=model_loaded,
        modelsAvailable=models_available,
        kafkaConnected=kafka_connected,
    )


@app.post("/api/v1/ml/detect", response_model=MlDetectionResult)
async def detect(payload: AggregatedAttackData) -> MlDetectionResult:
    return _run_inference_sync(payload)


@app.get("/api/v1/ml/models", response_model=List[ModelInfo])
async def models() -> List[ModelInfo]:
    if not state.engine:
        return [
            ModelInfo(tier=1, available=False, threshold=settings.ml_confidence_threshold, modelPath=f"{settings.model_dir}/autoencoder_v1_tier1.onnx"),
            ModelInfo(tier=2, available=False, threshold=settings.ml_confidence_threshold, modelPath=f"{settings.model_dir}/autoencoder_v1_tier2.onnx"),
            ModelInfo(tier=3, available=False, threshold=settings.ml_confidence_threshold, modelPath=f"{settings.model_dir}/autoencoder_v1_tier3.onnx"),
        ]

    metadata = state.engine.model_metadata()
    return [
        ModelInfo(
            tier=tier,
            available=bool(info["available"]),
            threshold=float(info["threshold"]),
            modelPath=str(info["modelPath"]),
        )
        for tier, info in metadata.items()
    ]
