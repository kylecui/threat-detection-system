from __future__ import annotations

# pyright: reportMissingImports=false, reportMissingTypeArgument=false, reportArgumentType=false

import asyncio
import json
import logging
import time
from collections import deque
from datetime import datetime, timezone
from typing import Deque, Dict, Optional, Tuple

import numpy as np
from aiokafka import AIOKafkaConsumer
from pydantic import ValidationError

from app.features.extractor import FeatureExtractor
from app.features.sequence_builder import SequenceBuffer
from app.models.schemas import AggregatedAttackData, MlDetectionResult
from app.monitoring.drift import DriftMonitor
from app.persistence.db_writer import MlPredictionWriter
from app.serving.engine import InferenceEngine
from app.serving.ensemble import ensemble_anomaly_score
from app.serving.scorer import (
    anomaly_type,
    reconstruction_to_anomaly_score,
    score_to_weight,
)


logger = logging.getLogger(__name__)

EVICTION_INTERVAL_SECONDS = 60.0
DRIFT_CHECK_INTERVAL = 300
SHADOW_STATS_MAX_ENTRIES = 10_000


class ShadowStats:
    def __init__(self, max_entries: int = SHADOW_STATS_MAX_ENTRIES) -> None:
        self._champion_scores: Deque[float] = deque(maxlen=max_entries)
        self._challenger_scores: Deque[float] = deque(maxlen=max_entries)
        self._champion_weights: Deque[float] = deque(maxlen=max_entries)
        self._challenger_weights: Deque[float] = deque(maxlen=max_entries)
        self._per_tier: Dict[int, Dict[str, Deque[float]]] = {
            tier: {
                "champion_scores": deque(maxlen=max_entries),
                "challenger_scores": deque(maxlen=max_entries),
            }
            for tier in (1, 2, 3)
        }
        self._total: int = 0

    def record(
        self,
        tier: int,
        champion_score: float,
        challenger_score: float,
        champion_weight: float,
        challenger_weight: float,
    ) -> None:
        self._champion_scores.append(champion_score)
        self._challenger_scores.append(challenger_score)
        self._champion_weights.append(champion_weight)
        self._challenger_weights.append(challenger_weight)
        self._per_tier[tier]["champion_scores"].append(champion_score)
        self._per_tier[tier]["challenger_scores"].append(challenger_score)
        self._total += 1

    def get_stats(self) -> dict:
        if self._total == 0:
            return {"totalComparisons": 0}

        champion_arr = np.array(self._champion_scores)
        challenger_arr = np.array(self._challenger_scores)
        deltas = challenger_arr - champion_arr
        challenger_better = int(np.sum(challenger_arr < champion_arr))

        per_tier: Dict[str, object] = {}
        for tier in (1, 2, 3):
            ch = np.array(self._per_tier[tier]["champion_scores"])
            cl = np.array(self._per_tier[tier]["challenger_scores"])
            if len(ch) == 0:
                continue
            per_tier[f"tier{tier}"] = {
                "count": len(ch),
                "meanChampion": round(float(np.mean(ch)), 4),
                "meanChallenger": round(float(np.mean(cl)), 4),
                "meanDelta": round(float(np.mean(cl - ch)), 4),
            }

        return {
            "totalComparisons": self._total,
            "meanChampionScore": round(float(np.mean(champion_arr)), 4),
            "meanChallengerScore": round(float(np.mean(challenger_arr)), 4),
            "meanScoreDelta": round(float(np.mean(deltas)), 4),
            "meanAbsScoreDelta": round(float(np.mean(np.abs(deltas))), 4),
            "meanChampionWeight": round(
                float(np.mean(list(self._champion_weights))), 4
            ),
            "meanChallengerWeight": round(
                float(np.mean(list(self._challenger_weights))), 4
            ),
            "challengerBetterCount": challenger_better,
            "challengerBetterPct": round(
                challenger_better / len(champion_arr) * 100, 2
            ),
            "perTier": per_tier,
        }


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
        drift_monitor: Optional[DriftMonitor] = None,
        shadow_scoring_enabled: bool = False,
        db_writer: Optional[MlPredictionWriter] = None,
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
        self.drift_monitor = drift_monitor
        self.shadow_scoring_enabled = shadow_scoring_enabled
        self.db_writer = db_writer
        self.shadow_stats = ShadowStats()
        self._last_eviction = time.monotonic()
        self._last_drift_check = time.monotonic()
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
        msg_count = 0
        async for message in self._consumer:
            payload = message.value
            msg_count += 1
            if msg_count <= 5 or msg_count % 100 == 0:
                logger.info(
                    "Consumed message #%d from %s (offset=%d)",
                    msg_count,
                    message.topic,
                    message.offset,
                )
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
        if self.db_writer is not None:
            await self.db_writer.write(result)
        self._maybe_evict()
        self._maybe_check_drift()

    def _infer(self, data: AggregatedAttackData) -> MlDetectionResult:
        from prometheus_client import REGISTRY

        detections_total = REGISTRY._names_to_collectors.get(  # type: ignore[attr-defined]
            "ml_detections_total"
        )
        inference_duration = REGISTRY._names_to_collectors.get(  # type: ignore[attr-defined]
            "ml_inference_duration_seconds"
        )

        started_at = time.perf_counter()
        result: MlDetectionResult | None = None

        features = self.feature_extractor.extract(data)

        if self.drift_monitor is not None:
            self.drift_monitor.observe(data.tier, features)

        reconstructed, threshold = self.engine.predict(
            features, data.tier, customer_id=data.customerId
        )
        reconstructed_one = (
            reconstructed[0] if reconstructed.ndim == 2 else reconstructed
        )
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
            f"autoencoder_v1_tier{data.tier}"
            if self.engine.is_model_loaded(data.tier)
            else "fallback"
        )

        tier_alpha = self.bigru_ensemble_alpha
        if self.bigru_enabled and self.sequence_buffer is not None:
            tier_alpha = self.engine.get_optimal_alpha(
                data.tier, self.bigru_ensemble_alpha, customer_id=data.customerId
            )
            temporal_score, seq_len, ensemble_method, final_score, model_version = (
                self._apply_bigru(data, features, score, model_version, tier_alpha)
            )
            if ensemble_method == "ensemble" and self.engine.is_model_loaded(data.tier):
                weight = score_to_weight(final_score, confidence)

        challenger_score: Optional[float] = None
        challenger_weight: Optional[float] = None
        challenger_version: Optional[str] = None

        if self.shadow_scoring_enabled and self.engine.is_challenger_loaded(data.tier):
            challenger_score, challenger_weight, challenger_version = (
                self._run_challenger(data, features, confidence)
            )
            self.shadow_stats.record(
                data.tier, final_score, challenger_score, weight, challenger_weight
            )

        try:
            result = MlDetectionResult(
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
                ensembleAlpha=tier_alpha,
                challengerScore=challenger_score,
                challengerWeight=challenger_weight,
                challengerVersion=challenger_version,
                timestamp=datetime.now(timezone.utc).isoformat(),
            )
            return result
        finally:
            if inference_duration is not None:
                inference_duration.labels(tier=str(data.tier)).observe(  # type: ignore[union-attr]
                    time.perf_counter() - started_at
                )
            if result is not None:
                if detections_total is not None:
                    detections_total.labels(  # type: ignore[union-attr]
                        tier=str(data.tier), anomaly_type=result.anomalyType
                    ).inc()

    def _apply_bigru(
        self,
        data: AggregatedAttackData,
        features: np.ndarray,
        ae_score: float,
        model_version: str,
        alpha: float | None = None,
    ) -> tuple[float, int, str, float, str]:
        assert self.sequence_buffer is not None
        if alpha is None:
            alpha = self.bigru_ensemble_alpha

        self.sequence_buffer.append(
            data.customerId, data.attackMac, data.tier, features
        )
        seq_data = self.sequence_buffer.get_sequence(
            data.customerId, data.attackMac, data.tier
        )

        if seq_data is None:
            return 0.0, 0, "autoencoder_only", ae_score, model_version

        padded, mask, seq_len = seq_data
        bigru_pred: Optional[float] = None

        if self.engine.is_bigru_loaded(data.tier) and seq_len >= self.bigru_min_seq_len:
            features_seq = np.expand_dims(padded, axis=0)
            mask_batch = np.expand_dims(mask, axis=0)
            bigru_pred = self.engine.predict_bigru(
                features_seq, mask_batch, data.tier, customer_id=data.customerId
            )

        temporal_score = bigru_pred if bigru_pred is not None else 0.0
        combined, method = ensemble_anomaly_score(
            ae_score,
            bigru_pred,
            seq_len,
            min_seq_len=self.bigru_min_seq_len,
            alpha=alpha,
        )

        if method == "ensemble":
            model_version = f"ensemble_v1_tier{data.tier}"

        return temporal_score, seq_len, method, combined, model_version

    def _run_challenger(
        self,
        data: AggregatedAttackData,
        features: np.ndarray,
        confidence: float,
    ) -> Tuple[float, float, str]:
        result = self.engine.predict_challenger(features, data.tier)
        if result is None:
            return 0.0, self.default_weight, "challenger_unavailable"

        ch_reconstructed, ch_threshold = result
        ch_reconstructed_one = (
            ch_reconstructed[0] if ch_reconstructed.ndim == 2 else ch_reconstructed
        )
        ch_rec_error = float(np.mean((features - ch_reconstructed_one) ** 2))
        ch_score = reconstruction_to_anomaly_score(ch_rec_error, ch_threshold)
        ch_weight = score_to_weight(ch_score, confidence)
        ch_version = f"challenger_tier{data.tier}"
        return ch_score, ch_weight, ch_version

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

    def _maybe_check_drift(self) -> None:
        if self.drift_monitor is None:
            return
        now = time.monotonic()
        if now - self._last_drift_check < DRIFT_CHECK_INTERVAL:
            return
        self._last_drift_check = now
        for tier in (1, 2, 3):
            drift_result = self.drift_monitor.compute_drift(tier)
            if drift_result and drift_result.get("total_psi", 0) > 0.2:
                logger.warning(
                    "Drift detected for tier %d: PSI=%.4f",
                    tier,
                    drift_result["total_psi"],
                )

    @property
    def connected(self) -> bool:
        return self._started
