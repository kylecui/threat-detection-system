"""Per-attacker rolling sequence buffer for BiGRU temporal model.

Maintains per-(customerId, attackMac, tier) feature deques with LRU + TTL
eviction.  Max 10K total entries across all keys.
"""

from __future__ import annotations

import time
from collections import OrderedDict
from dataclasses import dataclass, field
from typing import Dict, Optional, Tuple

import numpy as np

# Tier → max sequence length
DEFAULT_MAX_SEQ_LENS: Dict[int, int] = {1: 32, 2: 32, 3: 48}

# Tier → TTL in seconds
DEFAULT_TTL: Dict[int, int] = {1: 1800, 2: 10800, 3: 86400}

FEATURE_DIM = 12

BufferKey = Tuple[str, str, int]  # (customerId, attackMac, tier)


@dataclass
class _SequenceEntry:
    features: np.ndarray  # shape (12,)
    timestamp: float  # time.monotonic()


@dataclass
class _AttackerBuffer:
    entries: list = field(default_factory=list)
    max_len: int = 32
    last_access: float = field(default_factory=time.monotonic)

    def append(self, features: np.ndarray) -> None:
        self.entries.append(_SequenceEntry(features=features, timestamp=time.monotonic()))
        if len(self.entries) > self.max_len:
            self.entries = self.entries[-self.max_len :]
        self.last_access = time.monotonic()

    def touch(self) -> None:
        self.last_access = time.monotonic()


class SequenceBuffer:
    """Thread-unsafe rolling buffer.  Designed to be used from a single
    asyncio event-loop (the Kafka consumer coroutine)."""

    def __init__(
        self,
        max_seq_lens: Optional[Dict[int, int]] = None,
        ttls: Optional[Dict[int, int]] = None,
        max_total_entries: int = 10_000,
    ) -> None:
        self._max_seq_lens = max_seq_lens or dict(DEFAULT_MAX_SEQ_LENS)
        self._ttls = ttls or dict(DEFAULT_TTL)
        self._max_total = max_total_entries
        # OrderedDict for LRU ordering (most-recently-used at end)
        self._buffers: OrderedDict[BufferKey, _AttackerBuffer] = OrderedDict()
        self._total_windows: int = 0

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def append(
        self, customer_id: str, attack_mac: str, tier: int, features: np.ndarray
    ) -> None:
        """Append a single feature window to the attacker's rolling buffer."""
        key: BufferKey = (customer_id, attack_mac, tier)
        buf = self._buffers.get(key)
        if buf is None:
            max_len = self._max_seq_lens.get(tier, 32)
            buf = _AttackerBuffer(max_len=max_len)
            self._buffers[key] = buf
        else:
            # Move to end (most recently used)
            self._buffers.move_to_end(key)

        old_len = len(buf.entries)
        buf.append(features)
        new_len = len(buf.entries)
        self._total_windows += new_len - old_len

        # Enforce global cap via LRU eviction
        while self._total_windows > self._max_total and self._buffers:
            self._evict_lru()

    def get_sequence(
        self, customer_id: str, attack_mac: str, tier: int
    ) -> Optional[Tuple[np.ndarray, np.ndarray, int]]:
        """Return padded sequence, mask, and actual length.

        Returns:
            (padded [max_seq_len, 12], mask [max_seq_len], length)  or  None
        """
        key: BufferKey = (customer_id, attack_mac, tier)
        buf = self._buffers.get(key)
        if buf is None or len(buf.entries) == 0:
            return None

        buf.touch()
        self._buffers.move_to_end(key)

        max_len = self._max_seq_lens.get(tier, 32)
        actual_len = len(buf.entries)

        # Stack features → (actual_len, 12)
        stacked = np.stack([e.features for e in buf.entries], axis=0).astype(np.float32)

        # Pad to max_len
        padded = np.zeros((max_len, FEATURE_DIM), dtype=np.float32)
        padded[:actual_len] = stacked

        mask = np.zeros(max_len, dtype=np.float32)
        mask[:actual_len] = 1.0

        return padded, mask, actual_len

    def evict_expired(self) -> int:
        """Remove entries whose last access exceeds their tier TTL.

        Returns the number of keys removed.
        """
        now = time.monotonic()
        to_remove: list[BufferKey] = []
        for key, buf in self._buffers.items():
            tier = key[2]
            ttl = self._ttls.get(tier, 86400)
            if now - buf.last_access > ttl:
                to_remove.append(key)

        for key in to_remove:
            buf = self._buffers.pop(key)
            self._total_windows -= len(buf.entries)

        return len(to_remove)

    @property
    def total_keys(self) -> int:
        return len(self._buffers)

    @property
    def total_windows(self) -> int:
        return self._total_windows

    # ------------------------------------------------------------------
    # Internal
    # ------------------------------------------------------------------

    def _evict_lru(self) -> None:
        """Evict the least-recently-used key (front of OrderedDict)."""
        if not self._buffers:
            return
        _key, buf = self._buffers.popitem(last=False)
        self._total_windows -= len(buf.entries)
