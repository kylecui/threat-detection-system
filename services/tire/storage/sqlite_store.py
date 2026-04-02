"""
SQLite-based storage for caching.
"""

import sqlite3
import json
from datetime import datetime, timedelta
from typing import Any, Optional
from pathlib import Path


class SQLiteStore:
    """Simple SQLite key-value store with TTL support."""

    def __init__(self, db_path: str = "cache.db"):
        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._init_db()

    def _init_db(self):
        """Initialize the database and create tables."""
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS cache (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    expiry TEXT NOT NULL
                )
            """)
            # Create index on expiry for efficient cleanup
            conn.execute("""
                CREATE INDEX IF NOT EXISTS idx_expiry ON cache(expiry)
            """)

    def set(self, key: str, value: Any, ttl_seconds: int) -> None:
        """Store a value with TTL."""
        expiry = datetime.utcnow() + timedelta(seconds=ttl_seconds)
        expiry_str = expiry.isoformat()
        value_str = json.dumps(value)

        with sqlite3.connect(self.db_path) as conn:
            conn.execute(
                "INSERT OR REPLACE INTO cache (key, value, expiry) VALUES (?, ?, ?)",
                (key, value_str, expiry_str),
            )

    def get(self, key: str) -> Optional[Any]:
        """Retrieve a value if not expired."""
        now = datetime.utcnow().isoformat()

        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.execute(
                "SELECT value, expiry FROM cache WHERE key = ? AND expiry > ?",
                (key, now),
            )
            row = cursor.fetchone()

        if row:
            value_str, _ = row
            try:
                return json.loads(value_str)
            except json.JSONDecodeError:
                return None
        return None

    def delete(self, key: str) -> None:
        """Delete a key."""
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("DELETE FROM cache WHERE key = ?", (key,))

    def cleanup(self) -> int:
        """Remove expired entries. Returns number of deleted entries."""
        now = datetime.utcnow().isoformat()

        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.execute("DELETE FROM cache WHERE expiry <= ?", (now,))
            return cursor.rowcount

    def clear_all(self) -> None:
        """Clear all cache entries."""
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("DELETE FROM cache")
