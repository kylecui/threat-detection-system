"""Persist ML detection results to the ml_predictions table in PostgreSQL."""

from __future__ import annotations

import asyncio
import logging
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from typing import Optional

import psycopg2
import psycopg2.pool

from app.models.schemas import MlDetectionResult

logger = logging.getLogger(__name__)

INSERT_SQL = """
INSERT INTO ml_predictions (
    customer_id, attack_mac, attack_ip, tier,
    window_start, window_end,
    ml_score, ml_weight, ml_confidence,
    anomaly_type, reconstruction_error, threshold,
    model_version, created_at
) VALUES (
    %s, %s, %s, %s,
    to_timestamp(%s), to_timestamp(%s),
    %s, %s, %s,
    %s, %s, %s,
    %s, %s
)
ON CONFLICT (customer_id, attack_mac, tier, window_start)
DO UPDATE SET
    ml_score = EXCLUDED.ml_score,
    ml_weight = EXCLUDED.ml_weight,
    ml_confidence = EXCLUDED.ml_confidence,
    anomaly_type = EXCLUDED.anomaly_type,
    reconstruction_error = EXCLUDED.reconstruction_error,
    threshold = EXCLUDED.threshold,
    model_version = EXCLUDED.model_version,
    created_at = EXCLUDED.created_at
"""


class MlPredictionWriter:
    """Async-friendly writer that persists MlDetectionResult rows."""

    def __init__(self, database_url: str, min_conn: int = 1, max_conn: int = 4) -> None:
        self._pool: Optional[psycopg2.pool.ThreadedConnectionPool] = None
        self._database_url = database_url
        self._min_conn = min_conn
        self._max_conn = max_conn
        self._executor = ThreadPoolExecutor(max_workers=max_conn, thread_name_prefix="db-writer")
        self._started = False

    def start(self) -> None:
        try:
            self._pool = psycopg2.pool.ThreadedConnectionPool(
                self._min_conn, self._max_conn, self._database_url,
            )
            self._started = True
            logger.info("ML prediction DB writer started (pool %d-%d)", self._min_conn, self._max_conn)
        except Exception:
            logger.exception("Failed to create DB connection pool")
            self._started = False

    def stop(self) -> None:
        if self._pool:
            self._pool.closeall()
            self._pool = None
        self._executor.shutdown(wait=False)
        self._started = False
        logger.info("ML prediction DB writer stopped")

    async def write(self, result: MlDetectionResult) -> None:
        if not self._started or self._pool is None:
            return
        loop = asyncio.get_running_loop()
        try:
            await loop.run_in_executor(self._executor, self._write_sync, result)
        except Exception:
            logger.exception("Failed to persist ML prediction for %s/%s", result.customerId, result.attackMac)

    def _write_sync(self, result: MlDetectionResult) -> None:
        assert self._pool is not None
        conn = self._pool.getconn()
        try:
            with conn.cursor() as cur:
                # Parse window timestamps (they come as float epoch strings)
                window_start = _parse_epoch(result.windowStart)
                window_end = _parse_epoch(result.windowEnd)
                created_at = datetime.now(timezone.utc)

                cur.execute(INSERT_SQL, (
                    result.customerId,
                    result.attackMac,
                    result.attackIp,
                    result.tier,
                    window_start,
                    window_end,
                    result.mlScore,
                    result.mlWeight,
                    result.mlConfidence,
                    result.anomalyType,
                    result.reconstructionError,
                    result.threshold,
                    result.modelVersion,
                    created_at,
                ))
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            self._pool.putconn(conn)

    @property
    def connected(self) -> bool:
        return self._started


def _parse_epoch(value: str | float | None) -> float:
    """Convert window timestamp to epoch seconds (float)."""
    if value is None:
        return 0.0
    try:
        return float(value)
    except (ValueError, TypeError):
        return 0.0
