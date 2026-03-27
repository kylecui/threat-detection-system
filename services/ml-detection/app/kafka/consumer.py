from __future__ import annotations

import asyncio
import json
import logging
from datetime import datetime, timezone
from typing import Optional

import numpy as np
from aiokafka import AIOKafkaConsumer
from pydantic import ValidationError

from app.features.extractor import FeatureExtractor
from app.models.schemas import AggregatedAttackData, MlDetectionResult
from app.serving.engine import InferenceEngine
from app.serving.scorer import anomaly_type, reconstruction_to_anomaly_score, score_to_weight


logger = logging.getLogger(__name__)


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
    ) -> None:
        self.topic = topic
        self.producer = producer
        self.feature_extractor = feature_extractor
        self.engine = engine
        self.default_weight = default_weight
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

        return MlDetectionResult(
            customerId=data.customerId,
            attackMac=data.attackMac,
            attackIp=data.attackIp,
            tier=data.tier,
            windowStart=data.windowStart,
            windowEnd=data.windowEnd,
            mlScore=score,
            mlWeight=weight,
            mlConfidence=confidence,
            anomalyType=anomaly_type(score),
            reconstructionError=rec_error,
            threshold=threshold,
            modelVersion=f"autoencoder_v1_tier{data.tier}" if self.engine.is_model_loaded(data.tier) else "fallback",
            timestamp=datetime.now(timezone.utc).isoformat(),
        )

    @property
    def connected(self) -> bool:
        return self._started
