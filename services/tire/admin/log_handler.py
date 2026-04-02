"""
In-memory log handler with ring buffer and SSE streaming support.

Captures Python logging output for real-time display in the admin log viewer.
"""

import asyncio
import logging
import threading
from collections import deque
from datetime import datetime, timezone
from typing import Any, AsyncGenerator, Optional


class MemoryLogHandler(logging.Handler):
    """Logging handler that stores records in the LogStore ring buffer."""

    def __init__(self, store: "LogStore", level: int = logging.DEBUG) -> None:
        super().__init__(level)
        self._store = store

    def emit(self, record: logging.LogRecord) -> None:
        try:
            entry = {
                "id": self._store.next_id(),
                "timestamp": datetime.fromtimestamp(
                    record.created, tz=timezone.utc
                ).strftime("%Y-%m-%d %H:%M:%S.%f")[:-3],
                "level": record.levelname,
                "logger": record.name,
                "message": self.format(record),
            }
            self._store.add(entry)
        except Exception:
            self.handleError(record)


class LogStore:
    """Thread-safe singleton ring buffer for log entries with SSE streaming."""

    _instance: Optional["LogStore"] = None
    _lock_cls = threading.Lock()
    _entries: deque[dict[str, Any]]
    _counter: int
    _lock: threading.Lock
    _event: asyncio.Event
    _loop: Optional[asyncio.AbstractEventLoop]

    def __new__(cls) -> "LogStore":
        with cls._lock_cls:
            if cls._instance is None:
                inst = super().__new__(cls)
                inst._entries: deque[dict[str, Any]] = deque(maxlen=1000)
                inst._counter = 0
                inst._lock = threading.Lock()
                inst._event = asyncio.Event()
                inst._loop = None
                cls._instance = inst
            return cls._instance

    def bind_loop(self) -> None:
        """Bind the active event loop for thread-safe wakeups."""
        try:
            self._loop = asyncio.get_running_loop()
        except RuntimeError:
            self._loop = None

    def next_id(self) -> int:
        with self._lock:
            self._counter += 1
            return self._counter

    def add(self, entry: dict[str, Any]) -> None:
        with self._lock:
            self._entries.append(entry)
        if self._loop is not None:
            self._loop.call_soon_threadsafe(self._event.set)
        else:
            try:
                self._event.set()
            except RuntimeError:
                pass

    def get_entries(
        self,
        since_id: int = 0,
        level: Optional[str] = None,
        search: Optional[str] = None,
    ) -> list[dict[str, Any]]:
        """Return entries after *since_id*, optionally filtered."""
        with self._lock:
            entries = list(self._entries)
        result = [e for e in entries if e["id"] > since_id]
        if level:
            result = [e for e in result if e["level"] == level.upper()]
        if search:
            q = search.lower()
            result = [
                e
                for e in result
                if q in e["message"].lower() or q in e["logger"].lower()
            ]
        return result

    async def stream(
        self, since_id: int = 0, level: Optional[str] = None
    ) -> AsyncGenerator[dict[str, Any], None]:
        """Async generator yielding new log entries for SSE."""
        last_id = since_id
        while True:
            entries = self.get_entries(since_id=last_id, level=level)
            for entry in entries:
                yield entry
                last_id = entry["id"]
            # Reset and wait for the next batch
            self._event.clear()
            try:
                await asyncio.wait_for(self._event.wait(), timeout=15.0)
            except asyncio.TimeoutError:
                # Send a keepalive comment (caller handles formatting)
                yield {"keepalive": True}
