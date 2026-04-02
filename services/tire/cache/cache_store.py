"""
Cache store for threat intelligence data.
"""

from typing import Dict, Any, Optional
from pathlib import Path
import json
from models.ip_profile import IPProfile
from models.verdict import Verdict
from storage.sqlite_store import SQLiteStore
from app.config import settings


class CacheStore:
    """Cache store for TI data with TTL support."""

    def __init__(self, db_path: str = "cache/cache.db"):
        self.store = SQLiteStore(db_path)
        self.ttl_hours = settings.cache_ttl_hours
        self.ttl_seconds = self.ttl_hours * 3600

    def _make_key(self, prefix: str, identifier: str) -> str:
        """Generate cache key."""
        return f"{prefix}:{identifier}"

    def get_collector_results(self, ip: str) -> Optional[Dict[str, Any]]:
        """Get cached raw collector results."""
        key = self._make_key("collectors", ip)
        return self.store.get(key)

    def set_collector_results(self, ip: str, results: Dict[str, Any]) -> None:
        """Cache raw collector results."""
        key = self._make_key("collectors", ip)
        self.store.set(key, results, self.ttl_seconds)

    def get_normalized_profile(self, ip: str) -> Optional[IPProfile]:
        """Get cached normalized IP profile."""
        key = self._make_key("profile", ip)
        data = self.store.get(key)
        if data:
            try:
                return IPProfile.parse_obj(data)
            except Exception:
                return None
        return None

    def set_normalized_profile(self, ip: str, profile: IPProfile) -> None:
        """Cache normalized IP profile."""
        key = self._make_key("profile", ip)
        data = json.loads(profile.json())  # Convert to dict for storage
        self.store.set(key, data, self.ttl_seconds)

    def get_verdict(self, ip: str) -> Optional[Verdict]:
        """Get cached verdict."""
        key = self._make_key("verdict", ip)
        data = self.store.get(key)
        if data:
            try:
                return Verdict.parse_obj(data)
            except Exception:
                return None
        return None

    def set_verdict(self, ip: str, verdict: Verdict) -> None:
        """Cache verdict."""
        key = self._make_key("verdict", ip)
        data = json.loads(verdict.json())  # Convert to dict for storage
        self.store.set(key, data, self.ttl_seconds)

    def invalidate_ip(self, ip: str) -> None:
        """Invalidate all cache for an IP."""
        keys = [
            self._make_key("collectors", ip),
            self._make_key("profile", ip),
            self._make_key("verdict", ip),
        ]
        for key in keys:
            self.store.delete(key)

    def cleanup_expired(self) -> int:
        """Clean up expired entries. Returns number of deleted entries."""
        return self.store.cleanup()

    def clear_all_cache(self) -> None:
        """Clear all cache entries."""
        self.store.clear_all()
