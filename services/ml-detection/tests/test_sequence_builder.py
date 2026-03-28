"""Tests for SequenceBuffer — append, get_sequence, LRU + TTL eviction."""

from __future__ import annotations

import time
from unittest.mock import patch

import numpy as np
import pytest

from app.features.sequence_builder import (
    FEATURE_DIM,
    SequenceBuffer,
)


def _rand_features() -> np.ndarray:
    return np.random.randn(FEATURE_DIM).astype(np.float32)


# ------------------------------------------------------------------
# Basic append / get_sequence
# ------------------------------------------------------------------


class TestAppendAndGet:
    def test_single_append_returns_correct_shape(self):
        buf = SequenceBuffer()
        buf.append("c1", "AA:BB:CC", 1, _rand_features())
        result = buf.get_sequence("c1", "AA:BB:CC", 1)
        assert result is not None
        padded, mask, length = result
        assert padded.shape == (32, FEATURE_DIM)  # tier 1 max = 32
        assert mask.shape == (32,)
        assert length == 1
        assert mask[0] == 1.0
        assert mask[1] == 0.0

    def test_multiple_appends_accumulate(self):
        buf = SequenceBuffer()
        for _ in range(5):
            buf.append("c1", "AA:BB:CC", 2, _rand_features())
        result = buf.get_sequence("c1", "AA:BB:CC", 2)
        assert result is not None
        _, mask, length = result
        assert length == 5
        assert float(mask[:5].sum()) == 5.0
        assert float(mask[5:].sum()) == 0.0

    def test_get_missing_key_returns_none(self):
        buf = SequenceBuffer()
        assert buf.get_sequence("c1", "XX:YY:ZZ", 1) is None

    def test_different_tiers_are_separate(self):
        buf = SequenceBuffer()
        buf.append("c1", "AA:BB:CC", 1, _rand_features())
        buf.append("c1", "AA:BB:CC", 2, _rand_features())
        buf.append("c1", "AA:BB:CC", 2, _rand_features())

        r1 = buf.get_sequence("c1", "AA:BB:CC", 1)
        r2 = buf.get_sequence("c1", "AA:BB:CC", 2)
        assert r1 is not None and r1[2] == 1
        assert r2 is not None and r2[2] == 2

    def test_tier3_max_seq_len_48(self):
        buf = SequenceBuffer()
        for _ in range(50):
            buf.append("c1", "AA:BB:CC", 3, _rand_features())
        result = buf.get_sequence("c1", "AA:BB:CC", 3)
        assert result is not None
        padded, mask, length = result
        assert padded.shape == (48, FEATURE_DIM)
        assert length == 48  # capped at max_seq_len
        assert float(mask.sum()) == 48.0


# ------------------------------------------------------------------
# Overflow — sequence truncation keeps newest
# ------------------------------------------------------------------


class TestOverflow:
    def test_overflow_keeps_newest_windows(self):
        buf = SequenceBuffer(max_seq_lens={1: 4, 2: 4, 3: 4})
        features = [np.full(FEATURE_DIM, i, dtype=np.float32) for i in range(6)]
        for f in features:
            buf.append("c1", "AA:BB:CC", 1, f)

        result = buf.get_sequence("c1", "AA:BB:CC", 1)
        assert result is not None
        padded, _, length = result
        assert length == 4
        # Should keep windows 2, 3, 4, 5 (the last 4)
        np.testing.assert_array_almost_equal(padded[0], features[2])
        np.testing.assert_array_almost_equal(padded[3], features[5])


# ------------------------------------------------------------------
# LRU eviction
# ------------------------------------------------------------------


class TestLRUEviction:
    def test_lru_eviction_on_global_cap(self):
        # max_total_entries=5, max_seq_len=3 → can fit ~2 keys with 3 windows each
        buf = SequenceBuffer(
            max_seq_lens={1: 3, 2: 3, 3: 3},
            max_total_entries=5,
        )
        # Add 3 windows for key A
        for _ in range(3):
            buf.append("c1", "A", 1, _rand_features())
        # Add 3 windows for key B → total=6 → triggers eviction of A (LRU)
        for _ in range(3):
            buf.append("c1", "B", 1, _rand_features())

        # Key A should have been evicted
        assert buf.get_sequence("c1", "A", 1) is None
        assert buf.get_sequence("c1", "B", 1) is not None
        assert buf.total_windows <= 5

    def test_access_refreshes_lru_order(self):
        buf = SequenceBuffer(
            max_seq_lens={1: 2, 2: 2, 3: 2},
            max_total_entries=4,
        )
        # Add A (2 windows), B (2 windows) → total=4
        for _ in range(2):
            buf.append("c1", "A", 1, _rand_features())
        for _ in range(2):
            buf.append("c1", "B", 1, _rand_features())

        # Access A to refresh its LRU position
        buf.get_sequence("c1", "A", 1)

        # Add C (2 windows) → needs eviction, should evict B (now LRU)
        for _ in range(2):
            buf.append("c1", "C", 1, _rand_features())

        assert buf.get_sequence("c1", "A", 1) is not None  # kept (recently accessed)
        assert buf.get_sequence("c1", "B", 1) is None  # evicted (LRU)
        assert buf.get_sequence("c1", "C", 1) is not None


# ------------------------------------------------------------------
# TTL eviction
# ------------------------------------------------------------------


class TestTTLEviction:
    def test_expired_entries_are_evicted(self):
        buf = SequenceBuffer(ttls={1: 10, 2: 10, 3: 10})
        buf.append("c1", "A", 1, _rand_features())

        # Fast-forward time by 15 seconds
        for key_buf in buf._buffers.values():
            key_buf.last_access -= 15.0

        removed = buf.evict_expired()
        assert removed == 1
        assert buf.get_sequence("c1", "A", 1) is None
        assert buf.total_windows == 0

    def test_non_expired_entries_survive(self):
        buf = SequenceBuffer(ttls={1: 100, 2: 100, 3: 100})
        buf.append("c1", "A", 1, _rand_features())
        removed = buf.evict_expired()
        assert removed == 0
        assert buf.get_sequence("c1", "A", 1) is not None


# ------------------------------------------------------------------
# Mask correctness
# ------------------------------------------------------------------


class TestMaskCorrectness:
    def test_mask_matches_actual_length(self):
        buf = SequenceBuffer()
        for i in range(7):
            buf.append("c1", "A", 1, _rand_features())
        result = buf.get_sequence("c1", "A", 1)
        assert result is not None
        padded, mask, length = result
        assert length == 7
        assert float(mask[:7].sum()) == 7.0
        assert float(mask[7:].sum()) == 0.0
        # Padded region should be zeros
        np.testing.assert_array_equal(padded[7:], 0.0)

    def test_features_are_preserved_in_order(self):
        buf = SequenceBuffer(max_seq_lens={1: 10, 2: 10, 3: 10})
        features = [np.full(FEATURE_DIM, float(i), dtype=np.float32) for i in range(5)]
        for f in features:
            buf.append("c1", "A", 1, f)
        result = buf.get_sequence("c1", "A", 1)
        assert result is not None
        padded, _, length = result
        for i in range(5):
            np.testing.assert_array_almost_equal(padded[i], features[i])


# ------------------------------------------------------------------
# Property counters
# ------------------------------------------------------------------


class TestCounters:
    def test_total_keys_and_windows(self):
        buf = SequenceBuffer()
        buf.append("c1", "A", 1, _rand_features())
        buf.append("c1", "A", 1, _rand_features())
        buf.append("c1", "B", 2, _rand_features())
        assert buf.total_keys == 2
        assert buf.total_windows == 3
