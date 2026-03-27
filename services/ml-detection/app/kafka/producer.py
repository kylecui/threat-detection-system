from __future__ import annotations

import json

from aiokafka import AIOKafkaProducer

from app.models.schemas import MlDetectionResult


class MlDetectionProducer:
    def __init__(self, bootstrap_servers: str, topic: str) -> None:
        self.topic = topic
        self._producer = AIOKafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda value: json.dumps(value).encode("utf-8"),
            key_serializer=lambda value: value.encode("utf-8"),
        )
        self._started = False

    async def start(self) -> None:
        await self._producer.start()
        self._started = True

    async def stop(self) -> None:
        if self._started:
            await self._producer.stop()
            self._started = False

    async def publish(self, result: MlDetectionResult) -> None:
        key = f"{result.customerId}:{result.attackMac}"
        await self._producer.send_and_wait(
            self.topic,
            key=key,
            value=result.model_dump(),
        )

    @property
    def connected(self) -> bool:
        return self._started
