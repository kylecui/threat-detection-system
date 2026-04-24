from __future__ import annotations

# pyright: reportMissingImports=false, reportMissingTypeArgument=false

import asyncio
import logging
import time
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Dict, List, Literal, Optional, cast

import numpy as np
from fastapi import FastAPI, Response
from prometheus_client import (
    CONTENT_TYPE_LATEST,
    Counter,
    Gauge,
    Histogram,
    generate_latest,
)

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
    TrainRequest,
    TrainingStatusResponse,
)
from app.monitoring.drift import DriftMonitor
from app.persistence.db_writer import MlPredictionWriter
from app.serving.engine import InferenceEngine, get_engine
from app.serving.scorer import (
    anomaly_type,
    reconstruction_to_anomaly_score,
    score_to_weight,
)


logging.basicConfig(level=getattr(logging, settings.log_level.upper(), logging.INFO))
logger = logging.getLogger(__name__)


ML_DETECTIONS_TOTAL = Counter(
    "ml_detections_total",
    "Total ML detections by tier and anomaly type",
    ["tier", "anomaly_type"],
)
ML_INFERENCE_DURATION_SECONDS = Histogram(
    "ml_inference_duration_seconds",
    "ML inference duration in seconds by tier",
    ["tier"],
)
ML_MODEL_LOADED = Gauge(
    "ml_model_loaded",
    "Whether an ML model is loaded for a given tier",
    ["tier"],
)


class AppState:
    def __init__(self) -> None:
        self.engine: InferenceEngine | None = None
        self.extractor = FeatureExtractor()
        self.producer: MlDetectionProducer | None = None
        self.consumer: MlDetectionConsumer | None = None
        self.sequence_buffer: SequenceBuffer | None = None
        self.drift_monitor: DriftMonitor | None = None
        self.db_writer: MlPredictionWriter | None = None
        self._watch_task: Optional[asyncio.Task[None]] = None


state = AppState()


class _TrainingState:
    def __init__(self) -> None:
        self.training = False
        self.tiers: List[int] = []
        self.started_at: Optional[str] = None
        self.completed_at: Optional[str] = None
        self.elapsed_seconds: Optional[float] = None
        self.results: Optional[Dict[int, Dict[str, object]]] = None
        self.error: Optional[str] = None

    def to_response(self) -> TrainingStatusResponse:
        return TrainingStatusResponse(
            training=self.training,
            tiers=self.tiers,
            startedAt=self.started_at,
            completedAt=self.completed_at,
            elapsedSeconds=self.elapsed_seconds,
            results=self.results,
            error=self.error,
        )


_training_state = _TrainingState()


def _sync_model_loaded_metrics() -> None:
    for tier in (1, 2, 3):
        loaded = bool(state.engine and state.engine.is_model_loaded(tier))
        ML_MODEL_LOADED.labels(tier=str(tier)).set(1 if loaded else 0)


@asynccontextmanager
async def lifespan(_: FastAPI):
    state.engine = get_engine(settings.model_dir, settings.ml_confidence_threshold)
    _sync_model_loaded_metrics()
    state.producer = MlDetectionProducer(
        settings.kafka_bootstrap_servers, settings.kafka_output_topic
    )
    kafka_max_retries = 5
    kafka_base_delay = 2
    for attempt in range(1, kafka_max_retries + 1):
        try:
            await state.producer.start()
            logger.info("Kafka producer connected on attempt %d", attempt)
            break
        except Exception as exc:
            if attempt == kafka_max_retries:
                logger.error(
                    "Kafka producer failed after %d attempts, giving up: %s",
                    kafka_max_retries,
                    exc,
                )
                raise
            delay = kafka_base_delay * (2 ** (attempt - 1))
            logger.warning(
                "Kafka producer connection attempt %d/%d failed: %s. Retrying in %ds...",
                attempt,
                kafka_max_retries,
                exc,
                delay,
            )
            await asyncio.sleep(delay)

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

    state.db_writer = MlPredictionWriter(settings.database_url)
    state.db_writer.start()

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
        db_writer=state.db_writer,
    )
    for attempt in range(1, kafka_max_retries + 1):
        try:
            await state.consumer.start()
            logger.info("Kafka consumer connected on attempt %d", attempt)
            break
        except Exception as exc:
            if attempt == kafka_max_retries:
                logger.error(
                    "Kafka consumer failed after %d attempts, giving up: %s",
                    kafka_max_retries,
                    exc,
                )
                raise
            delay = kafka_base_delay * (2 ** (attempt - 1))
            logger.warning(
                "Kafka consumer connection attempt %d/%d failed: %s. Retrying in %ds...",
                attempt,
                kafka_max_retries,
                exc,
                delay,
            )
            await asyncio.sleep(delay)

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
        if state.db_writer:
            state.db_writer.stop()


async def _model_watch_loop() -> None:
    interval = settings.model_watch_interval_seconds
    while True:
        await asyncio.sleep(interval)
        if state.engine and state.engine.check_for_updates():
            logger.info("Model file change detected, triggering reload")
            state.engine.reload()
            _sync_model_loaded_metrics()


app = FastAPI(title="ML Threat Detection Service", version="2.0.0", lifespan=lifespan)


def _run_inference_sync(data: AggregatedAttackData) -> MlDetectionResult:
    if not state.engine:
        raise RuntimeError("Inference engine not initialized")

    started_at = time.perf_counter()
    result: MlDetectionResult | None = None
    anomaly_label = "error"
    try:
        features = state.extractor.extract(data)
        reconstruction, threshold = state.engine.predict(features, data.tier)
        reconstruction_one = (
            reconstruction[0] if reconstruction.ndim == 2 else reconstruction
        )
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

        result = MlDetectionResult(
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
        anomaly_label = result.anomalyType
        return result
    finally:
        ML_INFERENCE_DURATION_SECONDS.labels(tier=str(data.tier)).observe(
            time.perf_counter() - started_at
        )
        if result is not None:
            ML_DETECTIONS_TOTAL.labels(
                tier=str(data.tier), anomaly_type=anomaly_label
            ).inc()


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    model_loaded = bool(state.engine and state.engine.is_model_loaded())
    models_available: Dict[str, bool] = (
        state.engine.model_info()
        if state.engine
        else {"tier1": False, "tier2": False, "tier3": False}
    )
    kafka_connected = bool(
        state.producer
        and state.producer.connected
        and state.consumer
        and state.consumer.connected
    )
    return HealthResponse(
        status="ok",
        modelLoaded=model_loaded,
        modelsAvailable=models_available,
        kafkaConnected=kafka_connected,
    )


@app.get("/metrics")
async def metrics() -> Response:
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)


@app.post("/api/v1/ml/detect", response_model=MlDetectionResult)
async def detect(payload: AggregatedAttackData) -> MlDetectionResult:
    return _run_inference_sync(payload)


@app.get("/api/v1/ml/models", response_model=List[ModelInfo])
async def models() -> List[ModelInfo]:
    if not state.engine:
        return [
            ModelInfo(
                tier=1,
                available=False,
                threshold=settings.ml_confidence_threshold,
                modelPath=f"{settings.model_dir}/autoencoder_v1_tier1.onnx",
            ),
            ModelInfo(
                tier=2,
                available=False,
                threshold=settings.ml_confidence_threshold,
                modelPath=f"{settings.model_dir}/autoencoder_v1_tier2.onnx",
            ),
            ModelInfo(
                tier=3,
                available=False,
                threshold=settings.ml_confidence_threshold,
                modelPath=f"{settings.model_dir}/autoencoder_v1_tier3.onnx",
            ),
        ]

    metadata = state.engine.model_metadata()
    return [
        ModelInfo(
            tier=cast(Literal[1, 2, 3], tier),
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
    result = cast(Dict[str, object], state.engine.reload())
    _sync_model_loaded_metrics()
    return ReloadResponse(
        status=cast(str, result.get("status", "error")),
        reloadCount=cast(int, result.get("reloadCount", 0)),
        modelsLoaded=cast(Optional[Dict[str, bool]], result.get("modelsLoaded")),
        error=cast(Optional[str], result.get("error")),
    )


@app.get("/api/v1/ml/buffer/stats")
async def buffer_stats() -> dict[str, object]:
    if state.sequence_buffer is None:
        return {"enabled": False, "totalKeys": 0, "totalWindows": 0}
    return {
        "enabled": True,
        "totalKeys": state.sequence_buffer.total_keys,
        "totalWindows": state.sequence_buffer.total_windows,
    }


@app.get("/api/v1/ml/drift/status")
async def drift_status() -> dict[str, object]:
    if state.drift_monitor is None:
        return {"enabled": False}
    for tier in (1, 2, 3):
        state.drift_monitor.compute_drift(tier)
    return {"enabled": True, **state.drift_monitor.get_status()}


@app.get("/api/v1/ml/shadow/stats")
async def shadow_stats() -> dict[str, object]:
    if state.consumer is None:
        return {"enabled": False, "totalComparisons": 0}
    return {
        "enabled": settings.shadow_scoring_enabled,
        "challengerDir": settings.challenger_model_dir,
        "challengerLoaded": bool(state.engine and state.engine.is_challenger_loaded()),
        **state.consumer.shadow_stats.get_stats(),
    }


def _run_training(tiers: List[int], ae_epochs: int, bigru_epochs: int) -> None:
    from app.training.pipeline import TrainingPipeline

    _training_state.results = {}
    try:
        for tier in tiers:
            pipeline = TrainingPipeline(
                tier=tier,
                model_dir=settings.model_dir,
                ae_epochs=ae_epochs,
                bigru_epochs=bigru_epochs,
                database_url=settings.database_url,
            )
            _training_state.results[tier] = pipeline.run()

        if state.engine:
            state.engine.reload()
            _sync_model_loaded_metrics()

        _training_state.completed_at = datetime.now(timezone.utc).isoformat()
        started = datetime.fromisoformat(_training_state.started_at)
        _training_state.elapsed_seconds = round(
            (datetime.now(timezone.utc) - started).total_seconds(), 2
        )
        logger.info(
            "Training completed for tiers %s in %.1fs",
            tiers,
            _training_state.elapsed_seconds,
        )
    except Exception as exc:
        _training_state.error = str(exc)
        logger.error("Training failed: %s", exc)
    finally:
        _training_state.training = False


@app.post("/api/v1/ml/train")
async def trigger_training(req: TrainRequest = TrainRequest()) -> dict[str, object]:
    if _training_state.training:
        return {"status": "already_running", "tiers": _training_state.tiers}

    for t in req.tiers:
        if t not in (1, 2, 3):
            return {"status": "error", "error": f"Invalid tier: {t}"}

    _training_state.training = True
    _training_state.tiers = req.tiers
    _training_state.started_at = datetime.now(timezone.utc).isoformat()
    _training_state.completed_at = None
    _training_state.elapsed_seconds = None
    _training_state.results = None
    _training_state.error = None

    import threading

    threading.Thread(
        target=_run_training,
        args=(req.tiers, req.aeEpochs, req.bigruEpochs),
        daemon=True,
    ).start()

    return {"status": "training_started", "tiers": req.tiers}


@app.get("/api/v1/ml/train/status", response_model=TrainingStatusResponse)
async def training_status() -> TrainingStatusResponse:
    return _training_state.to_response()


@app.get("/api/v1/ml/train/data-readiness")
async def data_readiness() -> dict[str, object]:
    try:
        from app.training.trainer import load_from_postgres

        counts: Dict[int, int] = {}
        for tier in (1, 2, 3):
            features = load_from_postgres(settings.database_url, tier)
            counts[tier] = len(features)
        return {
            "ready": any(c > 0 for c in counts.values()),
            "sampleCounts": counts,
            "minimumRequired": {"autoencoder": 1, "bigru": 20},
        }
    except Exception as exc:
        return {"ready": False, "error": str(exc)}
