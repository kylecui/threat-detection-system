from __future__ import annotations

import asyncio
import json
import logging
import time
from datetime import datetime, timezone
from typing import Optional

import numpy as np
from aiokafka import AIOKafkaConsumer
from pydantic import ValidationError

from app.features.extractor import FeatureExtractor
from app.features.sequence_builder import SequenceBuffer
from app.models.schemas import AggregatedAttackData, MlDetectionResult
from app.serving.engine import InferenceEngine
from app.serving.ensemble import ensemble_anomaly_score
from app.serving.scorer import anomaly_type, reconstruction_to_anomaly_score, score_to_weight


logger = logging.getLogger(__name__)

EVICTION_INTERVAL_SECONDS = 60.0


class MlDetectionConsumer:
    def __init__(
        self,
        bootstrap_servers: str,
        topic: str,
        group_id: str,
        producer,
        feature_extractor: FeatureExtractor,
        engine: InferenceEngine,
        default_weight: float = 1.0,
        sequence_buffer: Optional[SequenceBuffer] = None,
        bigru_enabled: bool = False,
        bigru_min_seq_len: int = 4,
        bigru_ensemble_alpha: float = 0.6,
    ) -> None:
        self.topic = topic
        self.producer = producer
        self.feature_extractor = feature_extractor
        self.engine = engine
        self.default_weight = default_weight
        self.sequence_buffer = sequence_buffer
        self.bigru_enabled = bigru_enabled
        self.bigru_min_seq_len = bigru_min_seq_len
        self.bigru_ensemble_alpha = bigru_ensemble_alpha
        self._last_eviction = time.monotonic()
        self._consumer = AIOKafkaConsumer(
            topic,
            bootstrap_servers=bootstrap_servers,
            group_id=group_id,
            auto_offset_reset="earliest",
            enable_auto_commit=True,
            value_deserializer=lambda value: json.loads(value.decode("utf-8")),
        )
        self._running_task: Optional[asyncio.Task[None]] = None
        self._started = False

    async def start(self) -> None:
        await self._consumer.start()
        self._started = True
        self._running_task = asyncio.create_task(self._consume_loop())

    async def stop(self) -> None:
        if self._running_task:
            self._running_task.cancel()
            try:
                await self._running_task
            except asyncio.CancelledError:
                pass
        if self._started:
            await self._consumer.stop()
            self._started = False

    async def _consume_loop(self) -> None:
        async for message in self._consumer:
            payload = message.value
            await self.process_message(payload)

    async def process_message(self, payload: dict[str, object]) -> None:
        try:
            data = AggregatedAttackData.model_validate(payload)
        except ValidationError as exc:
            logger.warning("Skipping invalid message: %s", exc)
            return

        try:
            result = self._infer(data)
        except Exception:
            logger.exception("Inference error, sending fallback result")
            result = MlDetectionResult(
                customerId=data.customerId,
                attackMac=data.attackMac,
                attackIp=data.attackIp,
                tier=data.tier,
                windowStart=data.windowStart,
                windowEnd=data.windowEnd,
                mlScore=0.0,
                mlWeight=self.default_weight,
                mlConfidence=0.0,
                anomalyType="fallback",
                reconstructionError=0.0,
                threshold=0.0,
                modelVersion="fallback",
                timestamp=datetime.now(timezone.utc).isoformat(),
            )

        await self.producer.publish(result)
        self._maybe_evict()

    def _infer(self, data: AggregatedAttackData) -> MlDetectionResult:
        features = self.feature_extractor.extract(data)
        reconstructed, threshold = self.engine.predict(features, data.tier)
        reconstructed_one = reconstructed[0] if reconstructed.ndim == 2 else reconstructed
        rec_error = float(np.mean((features - reconstructed_one) ** 2))
        score = reconstruction_to_anomaly_score(rec_error, threshold)
        confidence = float(max(0.0, min(1.0, score)))

        if not self.engine.is_model_loaded(data.tier):
            weight = self.default_weight
        else:
            weight = score_to_weight(score, confidence)

        temporal_score = 0.0
        seq_len = 0
        ensemble_method = "autoencoder_only"
        final_score = score
        model_version = (
            f"autoencoder_v1_tier{data.tier}" if self.engine.is_model_loaded(data.tier) else "fallback"
        )

        if self.bigru_enabled and self.sequence_buffer is not None:
            temporal_score, seq_len, ensemble_method, final_score, model_version = (
                self._apply_bigru(data, features, score, model_version)
            )
            if ensemble_method == "ensemble" and self.engine.is_model_loaded(data.tier):
                weight = score_to_weight(final_score, confidence)

        return MlDetectionResult(
            customerId=data.customerId,
            attackMac=data.attackMac,
            attackIp=data.attackIp,
            tier=data.tier,
            windowStart=data.windowStart,
            windowEnd=data.windowEnd,
            mlScore=final_score,
            mlWeight=weight,
            mlConfidence=confidence,
            anomalyType=anomaly_type(final_score),
            reconstructionError=rec_error,
            threshold=threshold,
            modelVersion=model_version,
            sequenceLength=seq_len,
            temporalScore=temporal_score,
            ensembleMethod=ensemble_method,
            ensembleAlpha=self.bigru_ensemble_alpha,
            timestamp=datetime.now(timezone.utc).isoformat(),
        )

    def _apply_bigru(
        self,
        data: AggregatedAttackData,
        features: np.ndarray,
        ae_score: float,
        model_version: str,
    ) -> tuple[float, int, str, float, str]:
        assert self.sequence_buffer is not None

        self.sequence_buffer.append(data.customerId, data.attackMac, data.tier, features)
        seq_data = self.sequence_buffer.get_sequence(data.customerId, data.attackMac, data.tier)

        if seq_data is None:
            return 0.0, 0, "autoencoder_only", ae_score, model_version

        padded, mask, seq_len = seq_data
        bigru_pred: Optional[float] = None

        if self.engine.is_bigru_loaded(data.tier) and seq_len >= self.bigru_min_seq_len:
            features_seq = np.expand_dims(padded, axis=0)
            mask_batch = np.expand_dims(mask, axis=0)
            bigru_pred = self.engine.predict_bigru(features_seq, mask_batch, data.tier)

        temporal_score = bigru_pred if bigru_pred is not None else 0.0
        combined, method = ensemble_anomaly_score(
            ae_score, bigru_pred, seq_len,
            min_seq_len=self.bigru_min_seq_len,
            alpha=self.bigru_ensemble_alpha,
        )

        if method == "ensemble":
            model_version = f"ensemble_v1_tier{data.tier}"

        return temporal_score, seq_len, method, combined, model_version

    def _maybe_evict(self) -> None:
        if self.sequence_buffer is None:
            return
        now = time.monotonic()
        if now - self._last_eviction < EVICTION_INTERVAL_SECONDS:
            return
        self._last_eviction = now
        removed = self.sequence_buffer.evict_expired()
        if removed > 0:
            logger.info("Evicted %d expired sequence buffers", removed)

    @property
    def connected(self) -> bool:
        return self._started
