from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Dict, List, Optional

import numpy as np
from fastapi import FastAPI

from app.config import settings
from app.features.extractor import FeatureExtractor
from app.features.sequence_builder import SequenceBuffer
from app.kafka.consumer import MlDetectionConsumer
from app.kafka.producer import MlDetectionProducer
from app.models.schemas import (
    AggregatedAttackData,
    HealthResponse,
    MlDetectionResult,
    ModelInfo,
    ReloadResponse,
    ShadowComparisonStats,
)
from app.monitoring.drift import DriftMonitor
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
        self.sequence_buffer: SequenceBuffer | None = None
        self.drift_monitor: DriftMonitor | None = None
        self._watch_task: Optional[asyncio.Task[None]] = None


state = AppState()


@asynccontextmanager
async def lifespan(_: FastAPI):
    state.engine = get_engine(settings.model_dir, settings.ml_confidence_threshold)
    state.producer = MlDetectionProducer(settings.kafka_bootstrap_servers, settings.kafka_output_topic)
    await state.producer.start()

    if settings.bigru_enabled:
        state.sequence_buffer = SequenceBuffer(
            max_seq_lens={
                1: settings.bigru_max_seq_len_tier1,
                2: settings.bigru_max_seq_len_tier2,
                3: settings.bigru_max_seq_len_tier3,
            },
            ttls={
                1: settings.bigru_buffer_ttl_tier1,
                2: settings.bigru_buffer_ttl_tier2,
                3: settings.bigru_buffer_ttl_tier3,
            },
            max_total_entries=settings.bigru_buffer_max_entries,
        )

    if settings.drift_enabled:
        state.drift_monitor = DriftMonitor(
            window_size=settings.drift_window_size,
            psi_threshold=settings.drift_psi_threshold,
            baseline_dir=settings.drift_baseline_path,
        )
        state.drift_monitor.load_baselines()

    if settings.shadow_scoring_enabled and state.engine:
        state.engine.load_challenger(settings.challenger_model_dir)

    state.consumer = MlDetectionConsumer(
        bootstrap_servers=settings.kafka_bootstrap_servers,
        topic=settings.kafka_input_topic,
        group_id=settings.kafka_consumer_group,
        producer=state.producer,
        feature_extractor=state.extractor,
        engine=state.engine,
        default_weight=settings.ml_default_weight,
        sequence_buffer=state.sequence_buffer,
        bigru_enabled=settings.bigru_enabled,
        bigru_min_seq_len=settings.bigru_min_seq_len,
        bigru_ensemble_alpha=settings.bigru_ensemble_alpha,
        drift_monitor=state.drift_monitor,
        shadow_scoring_enabled=settings.shadow_scoring_enabled,
    )
    await state.consumer.start()

    if settings.model_watch_enabled and state.engine:
        state._watch_task = asyncio.create_task(_model_watch_loop())

    try:
        yield
    finally:
        if state._watch_task:
            state._watch_task.cancel()
            try:
                await state._watch_task
            except asyncio.CancelledError:
                pass
        if state.consumer:
            await state.consumer.stop()
        if state.producer:
            await state.producer.stop()


async def _model_watch_loop() -> None:
    interval = settings.model_watch_interval_seconds
    while True:
        await asyncio.sleep(interval)
        if state.engine and state.engine.check_for_updates():
            logger.info("Model file change detected, triggering reload")
            state.engine.reload()


app = FastAPI(title="ML Threat Detection Service", version="2.0.0", lifespan=lifespan)


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
            bigruAvailable=bool(info.get("bigruAvailable", False)),
            bigruModelPath=str(info.get("bigruModelPath", "")),
            optimalAlpha=float(info.get("optimalAlpha", 0.6)),
        )
        for tier, info in metadata.items()
    ]


@app.post("/api/v1/ml/models/reload", response_model=ReloadResponse)
async def reload_models() -> ReloadResponse:
    if not state.engine:
        return ReloadResponse(status="error", error="Engine not initialized")
    result = state.engine.reload()
    return ReloadResponse(
        status=result.get("status", "error"),
        reloadCount=result.get("reloadCount", 0),
        modelsLoaded=result.get("modelsLoaded"),
        error=result.get("error"),
    )


@app.get("/api/v1/ml/buffer/stats")
async def buffer_stats() -> dict:
    if state.sequence_buffer is None:
        return {"enabled": False, "totalKeys": 0, "totalWindows": 0}
    return {
        "enabled": True,
        "totalKeys": state.sequence_buffer.total_keys,
        "totalWindows": state.sequence_buffer.total_windows,
    }


@app.get("/api/v1/ml/drift/status")
async def drift_status() -> dict:
    if state.drift_monitor is None:
        return {"enabled": False}
    for tier in (1, 2, 3):
        state.drift_monitor.compute_drift(tier)
    return {"enabled": True, **state.drift_monitor.get_status()}


@app.get("/api/v1/ml/shadow/stats")
async def shadow_stats() -> dict:
    if state.consumer is None:
        return {"enabled": False, "totalComparisons": 0}
    return {
        "enabled": settings.shadow_scoring_enabled,
        "challengerDir": settings.challenger_model_dir,
        "challengerLoaded": bool(state.engine and state.engine.is_challenger_loaded()),
        **state.consumer.shadow_stats.get_stats(),
    }
