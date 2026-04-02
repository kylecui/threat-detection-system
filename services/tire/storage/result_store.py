"""
Persistent result store for TIRE V2.

Stores query snapshots, generated reports, and plugin API keys in a
dedicated SQLite database (storage/results.db), separate from the
TTL-based cache (cache/cache.db) and the admin portal DB (admin/admin.db).

Design decisions:
  - Raw sqlite3 + WAL mode (matches existing codebase pattern).
  - Old snapshots are archived, never overwritten — enables comparison.
  - Per-API-key sharing: shared-key results can be served to all users;
    personal-key results are isolated.
  - Plugin API keys are Fernet-encrypted at rest.
  - Staleness threshold (default 7 days) triggers re-query even if data
    is persisted.
"""

import json
import logging
import os
import sqlite3
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any, Optional

from cryptography.fernet import Fernet

logger = logging.getLogger(__name__)

# Default DB path: stored under data/ to avoid Docker volume shadowing code
_DEFAULT_DB_PATH = os.path.join(os.path.dirname(__file__), "..", "data", "results.db")

# Default staleness threshold in days
DEFAULT_STALENESS_DAYS = 7


def _utc_now_iso() -> str:
    """Return an ISO-8601 UTC timestamp string."""
    return datetime.now(UTC).isoformat()


class ResultStore:
    """SQLite-backed persistent store for query results, reports, and API keys.

    Tables:
      - query_snapshots: Archived IP query results (verdict + raw_sources).
      - stored_reports:  Generated narrative reports (HTML, per-user).
      - plugin_api_keys: Per-user and shared (admin) plugin API keys (Fernet-encrypted).
      - admin_key_policy: Admin controls for shared key usage.
    """

    def __init__(self, db_path: str | None = None, fernet_key: bytes | None = None):
        self.db_path = Path(db_path or _DEFAULT_DB_PATH).resolve()
        self.db_path.parent.mkdir(parents=True, exist_ok=True)

        # Fernet key for encrypting API keys at rest.
        # In production this MUST come from an env var or secrets manager.
        self._fernet = self._init_fernet(fernet_key)

        self._init_db()

    # ── Connection helpers ──────────────────────────────────────────

    def _get_conn(self) -> sqlite3.Connection:
        """Open a WAL-mode connection with Row factory."""
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA foreign_keys=ON")
        return conn

    @staticmethod
    def _init_fernet(key: bytes | None) -> Fernet:
        """Initialise the Fernet cipher.

        Priority: explicit key arg > TIRE_FERNET_KEY env var > auto-generate.
        Auto-generated keys are logged with a warning — they won't survive
        restarts (keys become unrecoverable).
        """
        if key:
            return Fernet(key)

        env_key = os.environ.get("TIRE_FERNET_KEY")
        if env_key:
            return Fernet(env_key.encode())

        generated = Fernet.generate_key()
        logger.warning(
            "No TIRE_FERNET_KEY configured — generated a temporary Fernet key. "
            "API keys stored this session will be UNRECOVERABLE after restart. "
            "Set TIRE_FERNET_KEY in .env for production use."
        )
        return Fernet(generated)

    # ── Schema ──────────────────────────────────────────────────────

    def _init_db(self) -> None:
        """Create tables and indexes if they don't exist."""
        with self._get_conn() as conn:
            conn.executescript("""
                -- Archived query snapshots (one row per query execution)
                CREATE TABLE IF NOT EXISTS query_snapshots (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    ip           TEXT    NOT NULL,
                    user_id      INTEGER,
                    api_key_type TEXT    NOT NULL DEFAULT 'shared',
                    verdict_json TEXT    NOT NULL,
                    sources_json TEXT,
                    final_score  INTEGER NOT NULL DEFAULT 0,
                    level        TEXT    NOT NULL DEFAULT 'Inconclusive',
                    queried_at   TEXT    NOT NULL,
                    is_archived  INTEGER NOT NULL DEFAULT 0
                );

                CREATE INDEX IF NOT EXISTS idx_qs_ip          ON query_snapshots(ip);
                CREATE INDEX IF NOT EXISTS idx_qs_ip_queried   ON query_snapshots(ip, queried_at);
                CREATE INDEX IF NOT EXISTS idx_qs_user         ON query_snapshots(user_id);

                -- Stored narrative reports
                CREATE TABLE IF NOT EXISTS stored_reports (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    ip              TEXT    NOT NULL,
                    user_id         INTEGER NOT NULL,
                    snapshot_id     INTEGER,
                    report_html     TEXT    NOT NULL,
                    llm_enhanced    INTEGER NOT NULL DEFAULT 0,
                    llm_fingerprint TEXT    NOT NULL DEFAULT '',
                    llm_source      TEXT    NOT NULL DEFAULT 'template',
                    lang            TEXT    NOT NULL DEFAULT 'en',
                    generated_at    TEXT    NOT NULL,
                    is_archived     INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (snapshot_id) REFERENCES query_snapshots(id)
                );

                CREATE INDEX IF NOT EXISTS idx_sr_ip_user ON stored_reports(ip, user_id);

                -- Per-user plugin API keys (Fernet-encrypted)
                CREATE TABLE IF NOT EXISTS plugin_api_keys (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id         INTEGER NOT NULL,
                    plugin_name     TEXT    NOT NULL,
                    encrypted_key   TEXT    NOT NULL,
                    updated_at      TEXT    NOT NULL,
                    UNIQUE(user_id, plugin_name)
                );

                -- Admin shared API keys (user_id = 0 by convention)
                -- Reuses plugin_api_keys with user_id = 0

                -- Policy: whether regular users may consume shared keys
                CREATE TABLE IF NOT EXISTS admin_key_policy (
                    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                    allow_shared_keys   INTEGER NOT NULL DEFAULT 1,
                    configured_only     INTEGER NOT NULL DEFAULT 1,
                    updated_at          TEXT    NOT NULL
                );

                CREATE TABLE IF NOT EXISTS plugin_usage_events (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    plugin_name     TEXT    NOT NULL,
                    user_id         INTEGER,
                    key_scope       TEXT    NOT NULL DEFAULT 'none',
                    success         INTEGER NOT NULL DEFAULT 0,
                    latency_ms      INTEGER,
                    status_code     INTEGER,
                    error_message   TEXT    NOT NULL DEFAULT '',
                    created_at      TEXT    NOT NULL
                );

                CREATE INDEX IF NOT EXISTS idx_plugin_usage_events_plugin
                    ON plugin_usage_events(plugin_name);
                CREATE INDEX IF NOT EXISTS idx_plugin_usage_events_user
                    ON plugin_usage_events(user_id);
                CREATE INDEX IF NOT EXISTS idx_plugin_usage_events_created
                    ON plugin_usage_events(created_at);

                CREATE TABLE IF NOT EXISTS llm_usage_events (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id         INTEGER,
                    source          TEXT    NOT NULL DEFAULT 'template',
                    fingerprint     TEXT    NOT NULL DEFAULT '',
                    shared_config_id INTEGER,
                    model           TEXT    NOT NULL DEFAULT '',
                    base_url        TEXT    NOT NULL DEFAULT '',
                    success         INTEGER NOT NULL DEFAULT 0,
                    latency_ms      INTEGER,
                    input_tokens    INTEGER NOT NULL DEFAULT 0,
                    output_tokens   INTEGER NOT NULL DEFAULT 0,
                    total_tokens    INTEGER NOT NULL DEFAULT 0,
                    status_code     INTEGER,
                    error_message   TEXT    NOT NULL DEFAULT '',
                    request_id      TEXT    NOT NULL DEFAULT '',
                    created_at      TEXT    NOT NULL
                );

                CREATE INDEX IF NOT EXISTS idx_llm_usage_events_user
                    ON llm_usage_events(user_id);
                CREATE INDEX IF NOT EXISTS idx_llm_usage_events_fingerprint
                    ON llm_usage_events(fingerprint);
                CREATE INDEX IF NOT EXISTS idx_llm_usage_events_created
                    ON llm_usage_events(created_at);
            """)
            columns = {
                row["name"]
                for row in conn.execute("PRAGMA table_info(stored_reports)").fetchall()
            }
            if "llm_fingerprint" not in columns:
                conn.execute(
                    "ALTER TABLE stored_reports ADD COLUMN llm_fingerprint TEXT NOT NULL DEFAULT ''"
                )
            if "llm_source" not in columns:
                conn.execute(
                    "ALTER TABLE stored_reports ADD COLUMN llm_source TEXT NOT NULL DEFAULT 'template'"
                )
            policy_columns = {
                row["name"]
                for row in conn.execute(
                    "PRAGMA table_info(admin_key_policy)"
                ).fetchall()
            }
            if "configured_only" not in policy_columns:
                conn.execute(
                    "ALTER TABLE admin_key_policy ADD COLUMN configured_only INTEGER NOT NULL DEFAULT 1"
                )
            llm_usage_columns = {
                row["name"]
                for row in conn.execute(
                    "PRAGMA table_info(llm_usage_events)"
                ).fetchall()
            }
            if llm_usage_columns and "shared_config_id" not in llm_usage_columns:
                conn.execute(
                    "ALTER TABLE llm_usage_events ADD COLUMN shared_config_id INTEGER"
                )
            # Ensure a default policy row exists
            row = conn.execute("SELECT COUNT(*) FROM admin_key_policy").fetchone()
            if row[0] == 0:
                conn.execute(
                    "INSERT INTO admin_key_policy (allow_shared_keys, configured_only, updated_at) VALUES (1, 1, ?)",
                    (_utc_now_iso(),),
                )

    # ── Query Snapshots ─────────────────────────────────────────────

    def save_snapshot(
        self,
        ip: str,
        verdict_json: str,
        sources_json: str | None,
        final_score: int,
        level: str,
        user_id: int | None = None,
        api_key_type: str = "shared",
    ) -> int:
        """Persist a query snapshot. Returns the new row id."""
        now = _utc_now_iso()
        with self._get_conn() as conn:
            cursor = conn.execute(
                """INSERT INTO query_snapshots
                   (ip, user_id, api_key_type, verdict_json, sources_json,
                    final_score, level, queried_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    ip,
                    user_id,
                    api_key_type,
                    verdict_json,
                    sources_json,
                    final_score,
                    level,
                    now,
                ),
            )
            return int(cursor.lastrowid or 0)

    def get_latest_snapshot(
        self,
        ip: str,
        user_id: int | None = None,
        api_key_type: str = "shared",
        staleness_days: int = DEFAULT_STALENESS_DAYS,
    ) -> Optional[dict[str, Any]]:
        """Return the most recent non-stale snapshot for an IP.

        For shared API keys, any user's snapshot is returned.
        For personal API keys, only the requesting user's snapshot is returned.

        Returns None if no snapshot exists or the latest one is stale.
        """
        cutoff = (datetime.now(UTC) - timedelta(days=staleness_days)).isoformat()

        with self._get_conn() as conn:
            if api_key_type == "shared":
                row = conn.execute(
                    """SELECT * FROM query_snapshots
                       WHERE ip = ? AND api_key_type = 'shared'
                         AND queried_at > ? AND is_archived = 0
                       ORDER BY queried_at DESC LIMIT 1""",
                    (ip, cutoff),
                ).fetchone()
            else:
                row = conn.execute(
                    """SELECT * FROM query_snapshots
                       WHERE ip = ? AND user_id = ? AND api_key_type = 'personal'
                         AND queried_at > ? AND is_archived = 0
                       ORDER BY queried_at DESC LIMIT 1""",
                    (ip, user_id, cutoff),
                ).fetchone()

        return dict(row) if row else None

    def get_snapshot_history(self, ip: str, limit: int = 20) -> list[dict[str, Any]]:
        """Return all snapshots for an IP, newest first (for comparison)."""
        with self._get_conn() as conn:
            rows = conn.execute(
                """SELECT id, ip, user_id, api_key_type, final_score, level,
                          queried_at, is_archived
                   FROM query_snapshots
                   WHERE ip = ?
                   ORDER BY queried_at DESC
                   LIMIT ?""",
                (ip, limit),
            ).fetchall()
        return [dict(r) for r in rows]

    def get_visible_snapshot_history(
        self, ip: str, user_id: int | None, is_admin: bool, limit: int = 20
    ) -> list[dict[str, Any]]:
        """Return snapshot history for an IP filtered by caller visibility."""
        with self._get_conn() as conn:
            if is_admin:
                rows = conn.execute(
                    """SELECT id, ip, user_id, api_key_type, final_score, level,
                              queried_at, is_archived
                       FROM query_snapshots
                       WHERE ip = ?
                       ORDER BY queried_at DESC
                       LIMIT ?""",
                    (ip, limit),
                ).fetchall()
            elif user_id is None:
                rows = conn.execute(
                    """SELECT id, ip, user_id, api_key_type, final_score, level,
                              queried_at, is_archived
                       FROM query_snapshots
                       WHERE ip = ? AND api_key_type = 'shared'
                       ORDER BY queried_at DESC
                       LIMIT ?""",
                    (ip, limit),
                ).fetchall()
            else:
                rows = conn.execute(
                    """SELECT id, ip, user_id, api_key_type, final_score, level,
                              queried_at, is_archived
                       FROM query_snapshots
                       WHERE ip = ? AND (api_key_type = 'shared' OR user_id = ?)
                       ORDER BY queried_at DESC
                       LIMIT ?""",
                    (ip, user_id, limit),
                ).fetchall()
        return [dict(r) for r in rows]

    def get_snapshot_by_id(self, snapshot_id: int) -> Optional[dict[str, Any]]:
        """Retrieve a single snapshot by its ID (for side-by-side diff)."""
        with self._get_conn() as conn:
            row = conn.execute(
                "SELECT * FROM query_snapshots WHERE id = ?",
                (snapshot_id,),
            ).fetchone()
        return dict(row) if row else None

    def get_user_snapshot_history(
        self, user_id: int, limit: int = 20
    ) -> list[dict[str, Any]]:
        """Return recent snapshots created by a specific user across IPs."""
        with self._get_conn() as conn:
            rows = conn.execute(
                """SELECT id, ip, user_id, api_key_type, final_score, level,
                          queried_at, is_archived
                   FROM query_snapshots
                   WHERE user_id = ?
                   ORDER BY queried_at DESC
                   LIMIT ?""",
                (user_id, limit),
            ).fetchall()
        return [dict(r) for r in rows]

    def archive_snapshots(self, ip: str) -> int:
        """Mark all current (non-archived) snapshots for an IP as archived.

        Called before saving a new refresh result to preserve history.
        Returns the number of rows archived.
        """
        now = _utc_now_iso()
        with self._get_conn() as conn:
            cursor = conn.execute(
                """UPDATE query_snapshots
                   SET is_archived = 1
                   WHERE ip = ? AND is_archived = 0""",
                (ip,),
            )
            return cursor.rowcount

    # ── Stored Reports ──────────────────────────────────────────────

    def save_report(
        self,
        ip: str,
        user_id: int,
        report_html: str,
        llm_enhanced: bool = False,
        llm_fingerprint: str = "",
        llm_source: str = "template",
        lang: str = "en",
        snapshot_id: int | None = None,
    ) -> int:
        """Persist a generated report. Returns the new row id."""
        now = _utc_now_iso()
        with self._get_conn() as conn:
            cursor = conn.execute(
                """INSERT INTO stored_reports
                   (ip, user_id, snapshot_id, report_html, llm_enhanced, llm_fingerprint,
                    llm_source, lang, generated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    ip,
                    user_id,
                    snapshot_id,
                    report_html,
                    int(llm_enhanced),
                    llm_fingerprint,
                    llm_source,
                    lang,
                    now,
                ),
            )
            return int(cursor.lastrowid or 0)

    def get_latest_report(
        self,
        ip: str,
        user_id: int,
        llm_fingerprint: str = "",
        lang: str = "en",
        snapshot_id: int | None = None,
    ) -> Optional[dict[str, Any]]:
        """Return the latest non-archived report for an IP+user+lang combo.

        Reports are per-user because different users have different LLM settings.
        """
        with self._get_conn() as conn:
            if snapshot_id is None:
                row = conn.execute(
                    """SELECT * FROM stored_reports
                       WHERE ip = ? AND user_id = ? AND lang = ?
                         AND llm_fingerprint = ? AND snapshot_id IS NULL AND is_archived = 0
                       ORDER BY generated_at DESC LIMIT 1""",
                    (ip, user_id, lang, llm_fingerprint),
                ).fetchone()
            else:
                row = conn.execute(
                    """SELECT * FROM stored_reports
                       WHERE ip = ? AND user_id = ? AND lang = ?
                         AND llm_fingerprint = ? AND snapshot_id = ? AND is_archived = 0
                       ORDER BY generated_at DESC LIMIT 1""",
                    (ip, user_id, lang, llm_fingerprint, snapshot_id),
                ).fetchone()
        return dict(row) if row else None

    def get_report_history(
        self, ip: str, user_id: int, limit: int = 20
    ) -> list[dict[str, Any]]:
        """Return report history for comparison."""
        with self._get_conn() as conn:
            rows = conn.execute(
                """SELECT id, ip, user_id, snapshot_id, llm_enhanced, llm_source, lang,
                           generated_at, is_archived
                   FROM stored_reports
                   WHERE ip = ? AND user_id = ?
                   ORDER BY generated_at DESC
                   LIMIT ?""",
                (ip, user_id, limit),
            ).fetchall()
        return [dict(r) for r in rows]

    def get_user_report_history(
        self, user_id: int, limit: int = 20
    ) -> list[dict[str, Any]]:
        """Return recent reports for a specific user across IPs."""
        with self._get_conn() as conn:
            rows = conn.execute(
                """SELECT id, ip, user_id, snapshot_id, llm_enhanced, llm_source,
                          lang, generated_at, is_archived
                   FROM stored_reports
                   WHERE user_id = ?
                   ORDER BY generated_at DESC
                   LIMIT ?""",
                (user_id, limit),
            ).fetchall()
        return [dict(r) for r in rows]

    def get_report_by_id(self, report_id: int) -> Optional[dict[str, Any]]:
        """Retrieve a single report by ID (for diff view)."""
        with self._get_conn() as conn:
            row = conn.execute(
                "SELECT * FROM stored_reports WHERE id = ?",
                (report_id,),
            ).fetchone()
        return dict(row) if row else None

    def archive_reports(self, ip: str, user_id: int) -> int:
        """Archive all current reports for an IP+user before regeneration."""
        with self._get_conn() as conn:
            cursor = conn.execute(
                """UPDATE stored_reports
                   SET is_archived = 1
                   WHERE ip = ? AND user_id = ? AND is_archived = 0""",
                (ip, user_id),
            )
            return cursor.rowcount

    # ── Plugin API Keys (Fernet-encrypted) ──────────────────────────

    def save_plugin_api_key(self, user_id: int, plugin_name: str, api_key: str) -> None:
        """Store or update an encrypted plugin API key for a user.

        Use user_id=0 for admin shared keys.
        """
        now = _utc_now_iso()
        encrypted = self._fernet.encrypt(api_key.encode()).decode()
        with self._get_conn() as conn:
            conn.execute(
                """INSERT INTO plugin_api_keys (user_id, plugin_name, encrypted_key, updated_at)
                   VALUES (?, ?, ?, ?)
                   ON CONFLICT(user_id, plugin_name) DO UPDATE SET
                     encrypted_key = excluded.encrypted_key,
                     updated_at = excluded.updated_at""",
                (user_id, plugin_name, encrypted, now),
            )

    def get_plugin_api_key(self, user_id: int, plugin_name: str) -> Optional[str]:
        """Retrieve and decrypt a plugin API key for a specific user.

        Returns None if no key is stored.
        """
        with self._get_conn() as conn:
            row = conn.execute(
                "SELECT encrypted_key FROM plugin_api_keys WHERE user_id = ? AND plugin_name = ?",
                (user_id, plugin_name),
            ).fetchone()
        if not row:
            return None
        try:
            return self._fernet.decrypt(row["encrypted_key"].encode()).decode()
        except Exception:
            logger.error(
                "Failed to decrypt API key for user=%s plugin=%s — key may be "
                "from a previous Fernet key. User must re-enter.",
                user_id,
                plugin_name,
            )
            return None

    def resolve_plugin_api_key(
        self, user_id: int, plugin_name: str, env_var: str | None = None
    ) -> tuple[Optional[str], str]:
        """Resolve the effective API key using the fallback chain.

        Fallback order: user_key -> shared_admin_key -> None.

        Returns:
            (api_key, source) where source is one of:
            'user', 'shared', 'none'
        """
        # 1. User's personal key
        user_key = self.get_plugin_api_key(user_id, plugin_name)
        if user_key:
            return user_key, "user"

        # 2. Shared admin key (user_id=0), if policy allows
        if self.is_shared_keys_allowed():
            shared_key = self.get_plugin_api_key(0, plugin_name)
            if shared_key:
                return shared_key, "shared"

        return None, "none"

    def delete_plugin_api_key(self, user_id: int, plugin_name: str) -> None:
        """Remove a stored plugin API key."""
        with self._get_conn() as conn:
            conn.execute(
                "DELETE FROM plugin_api_keys WHERE user_id = ? AND plugin_name = ?",
                (user_id, plugin_name),
            )

    def list_plugin_api_keys(self, user_id: int) -> list[dict[str, Any]]:
        """List all plugin API keys for a user (keys are NOT decrypted)."""
        with self._get_conn() as conn:
            rows = conn.execute(
                """SELECT plugin_name, updated_at
                   FROM plugin_api_keys
                   WHERE user_id = ?
                   ORDER BY plugin_name""",
                (user_id,),
            ).fetchall()
        return [dict(r) for r in rows]

    # ── API Usage Metrics ─────────────────────────────────────────────

    def record_plugin_usage(
        self,
        *,
        plugin_name: str,
        user_id: int | None,
        key_scope: str,
        success: bool,
        latency_ms: int | None = None,
        status_code: int | None = None,
        error_message: str = "",
    ) -> int:
        """Persist a plugin API usage event without storing secrets."""
        now = _utc_now_iso()
        with self._get_conn() as conn:
            cursor = conn.execute(
                """INSERT INTO plugin_usage_events
                   (plugin_name, user_id, key_scope, success, latency_ms, status_code,
                    error_message, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    plugin_name,
                    user_id,
                    key_scope,
                    int(success),
                    latency_ms,
                    status_code,
                    error_message[:500],
                    now,
                ),
            )
            return int(cursor.lastrowid or 0)

    def record_llm_usage(
        self,
        *,
        user_id: int | None,
        source: str,
        fingerprint: str,
        shared_config_id: int | None = None,
        model: str,
        base_url: str,
        success: bool,
        latency_ms: int | None = None,
        input_tokens: int = 0,
        output_tokens: int = 0,
        total_tokens: int = 0,
        status_code: int | None = None,
        error_message: str = "",
        request_id: str = "",
    ) -> int:
        """Persist an LLM usage event without storing API keys."""
        now = _utc_now_iso()
        with self._get_conn() as conn:
            cursor = conn.execute(
                """INSERT INTO llm_usage_events
                   (user_id, source, fingerprint, shared_config_id, model, base_url, success, latency_ms,
                    input_tokens, output_tokens, total_tokens, status_code,
                    error_message, request_id, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    user_id,
                    source,
                    fingerprint,
                    shared_config_id,
                    model,
                    base_url,
                    int(success),
                    latency_ms,
                    input_tokens,
                    output_tokens,
                    total_tokens,
                    status_code,
                    error_message[:500],
                    request_id[:200],
                    now,
                ),
            )
            return int(cursor.lastrowid or 0)

    @staticmethod
    def _window_start_iso(since_days: int) -> str:
        return (datetime.now(UTC) - timedelta(days=since_days)).isoformat()

    @staticmethod
    def _summarize_usage_rows(rows: list[sqlite3.Row]) -> dict[str, Any]:
        total_calls = len(rows)
        success_calls = sum(int(row["success"]) for row in rows)
        latency_values = [
            row["latency_ms"] for row in rows if row["latency_ms"] is not None
        ]
        return {
            "calls": total_calls,
            "success_calls": success_calls,
            "failure_calls": total_calls - success_calls,
            "success_rate": (success_calls / total_calls) if total_calls else 0.0,
            "avg_latency_ms": int(sum(latency_values) / len(latency_values))
            if latency_values
            else None,
            "last_used_at": rows[0]["created_at"] if rows else None,
        }

    def list_plugin_usage_summary(
        self,
        *,
        user_id: int | None = None,
        key_scope: str | None = None,
        include_shared_for_user: bool = False,
        since_days: int = 30,
    ) -> list[dict[str, Any]]:
        """Return aggregated plugin API usage grouped by plugin name."""
        cutoff = self._window_start_iso(since_days)
        conditions = ["created_at >= ?"]
        params: list[Any] = [cutoff]

        if user_id is not None:
            if include_shared_for_user:
                conditions.append(
                    "(user_id = ? OR (user_id IS NULL AND key_scope = 'shared'))"
                )
                params.append(user_id)
            else:
                conditions.append("user_id = ?")
                params.append(user_id)
        if key_scope is not None:
            conditions.append("key_scope = ?")
            params.append(key_scope)

        where = " AND ".join(conditions)
        with self._get_conn() as conn:
            rows = conn.execute(
                f"""SELECT plugin_name,
                           COUNT(*) AS calls,
                           SUM(success) AS success_calls,
                           AVG(latency_ms) AS avg_latency_ms,
                           MAX(created_at) AS last_used_at,
                           SUM(CASE WHEN key_scope = 'shared' THEN 1 ELSE 0 END) AS shared_calls,
                           SUM(CASE WHEN key_scope = 'user' THEN 1 ELSE 0 END) AS personal_calls,
                           SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failure_calls
                    FROM plugin_usage_events
                    WHERE {where}
                    GROUP BY plugin_name
                    ORDER BY calls DESC, plugin_name ASC""",
                params,
            ).fetchall()
        return [
            {
                "plugin_name": row["plugin_name"],
                "calls": int(row["calls"] or 0),
                "success_calls": int(row["success_calls"] or 0),
                "failure_calls": int(row["failure_calls"] or 0),
                "success_rate": (
                    float(row["success_calls"] or 0) / float(row["calls"])
                    if row["calls"]
                    else 0.0
                ),
                "avg_latency_ms": int(row["avg_latency_ms"])
                if row["avg_latency_ms"] is not None
                else None,
                "last_used_at": row["last_used_at"],
                "shared_calls": int(row["shared_calls"] or 0),
                "personal_calls": int(row["personal_calls"] or 0),
            }
            for row in rows
        ]

    def get_plugin_usage_overview(
        self,
        *,
        user_id: int | None = None,
        key_scope: str | None = None,
        include_shared_for_user: bool = False,
        since_days: int = 30,
    ) -> dict[str, Any]:
        """Return top-level plugin usage metrics for a given scope."""
        cutoff = self._window_start_iso(since_days)
        conditions = ["created_at >= ?"]
        params: list[Any] = [cutoff]

        if user_id is not None:
            if include_shared_for_user:
                conditions.append(
                    "(user_id = ? OR (user_id IS NULL AND key_scope = 'shared'))"
                )
                params.append(user_id)
            else:
                conditions.append("user_id = ?")
                params.append(user_id)
        if key_scope is not None:
            conditions.append("key_scope = ?")
            params.append(key_scope)

        where = " AND ".join(conditions)
        with self._get_conn() as conn:
            row = conn.execute(
                f"""SELECT COUNT(*) AS calls,
                           SUM(success) AS success_calls,
                           AVG(latency_ms) AS avg_latency_ms,
                           MAX(created_at) AS last_used_at
                    FROM plugin_usage_events
                    WHERE {where}""",
                params,
            ).fetchone()

        calls = int(row["calls"] or 0) if row else 0
        success_calls = int(row["success_calls"] or 0) if row else 0
        return {
            "calls": calls,
            "success_calls": success_calls,
            "failure_calls": calls - success_calls,
            "success_rate": (success_calls / calls) if calls else 0.0,
            "avg_latency_ms": int(row["avg_latency_ms"])
            if row and row["avg_latency_ms"] is not None
            else None,
            "last_used_at": row["last_used_at"] if row else None,
        }

    def list_llm_usage_summary(
        self,
        *,
        user_id: int | None = None,
        source: str | None = None,
        include_shared_for_user: bool = False,
        since_days: int = 30,
    ) -> list[dict[str, Any]]:
        """Return aggregated LLM usage grouped by fingerprint/source/model."""
        cutoff = self._window_start_iso(since_days)
        conditions = ["created_at >= ?"]
        params: list[Any] = [cutoff]

        if user_id is not None:
            if include_shared_for_user:
                conditions.append(
                    "(user_id = ? OR (user_id IS NULL AND source IN ('shared-user', 'shared-group')))"
                )
                params.append(user_id)
            else:
                conditions.append("user_id = ?")
                params.append(user_id)
        if source is not None:
            conditions.append("source = ?")
            params.append(source)

        where = " AND ".join(conditions)
        with self._get_conn() as conn:
            rows = conn.execute(
                f"""SELECT fingerprint, source, shared_config_id, model, base_url,
                           COUNT(*) AS calls,
                           SUM(success) AS success_calls,
                           SUM(total_tokens) AS total_tokens,
                           SUM(input_tokens) AS input_tokens,
                           SUM(output_tokens) AS output_tokens,
                           AVG(latency_ms) AS avg_latency_ms,
                           MAX(created_at) AS last_used_at
                    FROM llm_usage_events
                    WHERE {where}
                    GROUP BY fingerprint, source, shared_config_id, model, base_url
                    ORDER BY calls DESC, last_used_at DESC""",
                params,
            ).fetchall()
        return [
            {
                "fingerprint": row["fingerprint"],
                "source": row["source"],
                "shared_config_id": row["shared_config_id"],
                "model": row["model"],
                "base_url": row["base_url"],
                "calls": int(row["calls"] or 0),
                "success_calls": int(row["success_calls"] or 0),
                "failure_calls": int((row["calls"] or 0) - (row["success_calls"] or 0)),
                "success_rate": (
                    float(row["success_calls"] or 0) / float(row["calls"])
                    if row["calls"]
                    else 0.0
                ),
                "total_tokens": int(row["total_tokens"] or 0),
                "input_tokens": int(row["input_tokens"] or 0),
                "output_tokens": int(row["output_tokens"] or 0),
                "avg_latency_ms": int(row["avg_latency_ms"])
                if row["avg_latency_ms"] is not None
                else None,
                "last_used_at": row["last_used_at"],
            }
            for row in rows
        ]

    def get_llm_usage_overview(
        self,
        *,
        user_id: int | None = None,
        source: str | None = None,
        include_shared_for_user: bool = False,
        since_days: int = 30,
    ) -> dict[str, Any]:
        """Return top-level LLM usage metrics for a given scope."""
        cutoff = self._window_start_iso(since_days)
        conditions = ["created_at >= ?"]
        params: list[Any] = [cutoff]

        if user_id is not None:
            if include_shared_for_user:
                conditions.append(
                    "(user_id = ? OR (user_id IS NULL AND source IN ('shared-user', 'shared-group')))"
                )
                params.append(user_id)
            else:
                conditions.append("user_id = ?")
                params.append(user_id)
        if source is not None:
            conditions.append("source = ?")
            params.append(source)

        where = " AND ".join(conditions)
        with self._get_conn() as conn:
            row = conn.execute(
                f"""SELECT COUNT(*) AS calls,
                           SUM(success) AS success_calls,
                           SUM(total_tokens) AS total_tokens,
                           AVG(latency_ms) AS avg_latency_ms,
                           MAX(created_at) AS last_used_at
                    FROM llm_usage_events
                    WHERE {where}""",
                params,
            ).fetchone()

        calls = int(row["calls"] or 0) if row else 0
        success_calls = int(row["success_calls"] or 0) if row else 0
        return {
            "calls": calls,
            "success_calls": success_calls,
            "failure_calls": calls - success_calls,
            "success_rate": (success_calls / calls) if calls else 0.0,
            "total_tokens": int(row["total_tokens"] or 0) if row else 0,
            "avg_latency_ms": int(row["avg_latency_ms"])
            if row and row["avg_latency_ms"] is not None
            else None,
            "last_used_at": row["last_used_at"] if row else None,
        }

    def get_api_usage_snapshot(
        self,
        *,
        user_id: int | None = None,
        plugin_key_scope: str | None = None,
        llm_source: str | None = None,
        include_shared_for_user: bool = False,
        since_days: int = 30,
    ) -> dict[str, Any]:
        """Return combined plugin + LLM usage metrics for a scope."""
        plugin_summary = self.list_plugin_usage_summary(
            user_id=user_id,
            key_scope=plugin_key_scope,
            include_shared_for_user=include_shared_for_user,
            since_days=since_days,
        )
        llm_summary = self.list_llm_usage_summary(
            user_id=user_id,
            source=llm_source,
            include_shared_for_user=include_shared_for_user,
            since_days=since_days,
        )
        return {
            "since_days": since_days,
            "plugin_overview": self.get_plugin_usage_overview(
                user_id=user_id,
                key_scope=plugin_key_scope,
                include_shared_for_user=include_shared_for_user,
                since_days=since_days,
            ),
            "plugin_summary": plugin_summary,
            "llm_overview": self.get_llm_usage_overview(
                user_id=user_id,
                source=llm_source,
                include_shared_for_user=include_shared_for_user,
                since_days=since_days,
            ),
            "llm_summary": llm_summary,
        }

    # ── Admin Key Policy ────────────────────────────────────────────

    def is_shared_keys_allowed(self) -> bool:
        """Check whether the admin allows regular users to use shared keys."""
        with self._get_conn() as conn:
            row = conn.execute(
                "SELECT allow_shared_keys FROM admin_key_policy ORDER BY id DESC LIMIT 1"
            ).fetchone()
        return bool(row and row["allow_shared_keys"])

    def is_configured_only(self) -> bool:
        """Whether runtime key resolution must ignore environment variables."""
        with self._get_conn() as conn:
            row = conn.execute(
                "SELECT configured_only FROM admin_key_policy ORDER BY id DESC LIMIT 1"
            ).fetchone()
        return bool(row and row["configured_only"])

    def set_shared_keys_policy(self, allowed: bool) -> None:
        """Update the shared-key policy."""
        now = _utc_now_iso()
        with self._get_conn() as conn:
            conn.execute(
                """UPDATE admin_key_policy
                   SET allow_shared_keys = ?, updated_at = ?
                   WHERE id = (SELECT id FROM admin_key_policy ORDER BY id DESC LIMIT 1)""",
                (int(allowed), now),
            )

    def set_configured_only_policy(self, configured_only: bool) -> None:
        """Update whether runtime should only use configured keys."""
        now = _utc_now_iso()
        with self._get_conn() as conn:
            conn.execute(
                """UPDATE admin_key_policy
                   SET configured_only = ?, updated_at = ?
                   WHERE id = (SELECT id FROM admin_key_policy ORDER BY id DESC LIMIT 1)""",
                (int(configured_only), now),
            )


# Singleton instance
result_store = ResultStore()


def get_result_store() -> ResultStore:
    """Return the current result-store singleton."""
    return result_store
