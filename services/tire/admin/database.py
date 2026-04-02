"""Admin portal persistence: users, policy groups, and LLM assignment."""

from __future__ import annotations

import json
import hashlib
import logging
import os
import sqlite3
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, Optional

import bcrypt
from cryptography.fernet import Fernet

logger = logging.getLogger(__name__)

_DEFAULT_DB_PATH = os.path.join(os.path.dirname(__file__), "..", "data", "admin.db")


def _utc_now_iso() -> str:
    return datetime.now(UTC).isoformat()


def _normalize_base_url(value: str) -> str:
    return value.rstrip("/") if value else ""


class AdminDB:
    """SQLite-backed admin database for users, groups, and LLM access."""

    def __init__(self, db_path: str | None = None):
        self.db_path = Path(db_path or _DEFAULT_DB_PATH).resolve()
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._fernet = self._init_fernet()
        self._init_db()

    def _get_conn(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA foreign_keys=ON")
        return conn

    @staticmethod
    def _init_fernet() -> Fernet:
        env_key = os.environ.get("TIRE_FERNET_KEY")
        if env_key:
            return Fernet(env_key.encode())
        generated = Fernet.generate_key()
        logger.warning(
            "No TIRE_FERNET_KEY configured — generated a temporary Fernet key for admin DB secrets. "
            "Stored LLM keys will be unrecoverable after restart."
        )
        return Fernet(generated)

    def _ensure_column(self, table: str, column: str, definition: str) -> None:
        with self._get_conn() as conn:
            rows = conn.execute(f"PRAGMA table_info({table})").fetchall()
            columns = {row["name"] for row in rows}
            if column not in columns:
                conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")

    def _init_db(self) -> None:
        with self._get_conn() as conn:
            conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS users (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    username        TEXT    UNIQUE NOT NULL,
                    password_hash   TEXT    NOT NULL,
                    display_name    TEXT    NOT NULL DEFAULT '',
                    is_admin        INTEGER NOT NULL DEFAULT 0,
                    is_active       INTEGER NOT NULL DEFAULT 1,
                    preferences     TEXT    NOT NULL DEFAULT '{}',
                    created_at      TEXT    NOT NULL,
                    updated_at      TEXT    NOT NULL
                );

                CREATE TABLE IF NOT EXISTS llm_settings (
                    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id             INTEGER NOT NULL UNIQUE,
                    api_key             TEXT    DEFAULT '',
                    encrypted_api_key   TEXT    DEFAULT '',
                    model               TEXT    NOT NULL DEFAULT 'gpt-4o',
                    base_url            TEXT    NOT NULL DEFAULT 'https://api.openai.com/v1',
                    updated_at          TEXT    NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );

                CREATE TABLE IF NOT EXISTS audit_log (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id     INTEGER,
                    action      TEXT    NOT NULL,
                    detail      TEXT    DEFAULT '',
                    created_at  TEXT    NOT NULL
                );

                CREATE TABLE IF NOT EXISTS policy_groups (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    name        TEXT    UNIQUE NOT NULL,
                    description TEXT    NOT NULL DEFAULT '',
                    priority    INTEGER NOT NULL DEFAULT 100,
                    created_at  TEXT    NOT NULL,
                    updated_at  TEXT    NOT NULL
                );

                CREATE TABLE IF NOT EXISTS policy_group_members (
                    user_id     INTEGER NOT NULL,
                    group_id    INTEGER NOT NULL,
                    created_at  TEXT    NOT NULL,
                    PRIMARY KEY (user_id, group_id),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (group_id) REFERENCES policy_groups(id) ON DELETE CASCADE
                );

                CREATE TABLE IF NOT EXISTS user_plugin_permissions (
                    user_id          INTEGER NOT NULL,
                    plugin_name      TEXT    NOT NULL,
                    can_use_shared   INTEGER NOT NULL,
                    updated_at       TEXT    NOT NULL,
                    PRIMARY KEY (user_id, plugin_name),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );

                CREATE TABLE IF NOT EXISTS group_plugin_permissions (
                    group_id         INTEGER NOT NULL,
                    plugin_name      TEXT    NOT NULL,
                    can_use_shared   INTEGER NOT NULL,
                    updated_at       TEXT    NOT NULL,
                    PRIMARY KEY (group_id, plugin_name),
                    FOREIGN KEY (group_id) REFERENCES policy_groups(id) ON DELETE CASCADE
                );

                CREATE TABLE IF NOT EXISTS shared_llm_configs (
                    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                    name                TEXT    UNIQUE NOT NULL,
                    encrypted_api_key   TEXT    NOT NULL,
                    model               TEXT    NOT NULL,
                    base_url            TEXT    NOT NULL,
                    is_active           INTEGER NOT NULL DEFAULT 1,
                    created_at          TEXT    NOT NULL,
                    updated_at          TEXT    NOT NULL
                );

                CREATE TABLE IF NOT EXISTS user_llm_assignments (
                    user_id         INTEGER PRIMARY KEY,
                    llm_config_id   INTEGER,
                    updated_at      TEXT    NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (llm_config_id) REFERENCES shared_llm_configs(id) ON DELETE SET NULL
                );

                CREATE TABLE IF NOT EXISTS group_llm_assignments (
                    group_id        INTEGER PRIMARY KEY,
                    llm_config_id   INTEGER,
                    updated_at      TEXT    NOT NULL,
                    FOREIGN KEY (group_id) REFERENCES policy_groups(id) ON DELETE CASCADE,
                    FOREIGN KEY (llm_config_id) REFERENCES shared_llm_configs(id) ON DELETE SET NULL
                );

                CREATE TABLE IF NOT EXISTS llm_policy (
                    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
                    allow_personal_llm_default  INTEGER NOT NULL DEFAULT 0,
                    updated_at                  TEXT    NOT NULL
                );

                CREATE TABLE IF NOT EXISTS user_llm_permissions (
                    user_id                INTEGER PRIMARY KEY,
                    allow_personal_llm     INTEGER,
                    updated_at             TEXT    NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );

                CREATE TABLE IF NOT EXISTS group_llm_permissions (
                    group_id               INTEGER PRIMARY KEY,
                    allow_personal_llm     INTEGER,
                    updated_at             TEXT    NOT NULL,
                    FOREIGN KEY (group_id) REFERENCES policy_groups(id) ON DELETE CASCADE
                );

                CREATE TABLE IF NOT EXISTS user_llm_allowlist (
                    user_id         INTEGER NOT NULL,
                    llm_config_id   INTEGER NOT NULL,
                    allowed         INTEGER NOT NULL,
                    updated_at      TEXT    NOT NULL,
                    PRIMARY KEY (user_id, llm_config_id),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (llm_config_id) REFERENCES shared_llm_configs(id) ON DELETE CASCADE
                );

                CREATE TABLE IF NOT EXISTS group_llm_allowlist (
                    group_id        INTEGER NOT NULL,
                    llm_config_id   INTEGER NOT NULL,
                    allowed         INTEGER NOT NULL,
                    updated_at      TEXT    NOT NULL,
                    PRIMARY KEY (group_id, llm_config_id),
                    FOREIGN KEY (group_id) REFERENCES policy_groups(id) ON DELETE CASCADE,
                    FOREIGN KEY (llm_config_id) REFERENCES shared_llm_configs(id) ON DELETE CASCADE
                );
                """
            )

        self._ensure_column("users", "is_active", "INTEGER NOT NULL DEFAULT 1")
        self._ensure_column("llm_settings", "encrypted_api_key", "TEXT DEFAULT ''")
        self._ensure_column(
            "llm_policy", "allow_shared_llm_default", "INTEGER NOT NULL DEFAULT 0"
        )
        with self._get_conn() as conn:
            row = conn.execute("SELECT COUNT(*) AS count FROM llm_policy").fetchone()
            if row and row["count"] == 0:
                conn.execute(
                    "INSERT INTO llm_policy (allow_personal_llm_default, allow_shared_llm_default, updated_at) VALUES (0, 0, ?)",
                    (_utc_now_iso(),),
                )

    def _encrypt_secret(self, value: str) -> str:
        if not value:
            return ""
        return self._fernet.encrypt(value.encode()).decode()

    def _decrypt_secret(self, value: str) -> str:
        if not value:
            return ""
        try:
            return self._fernet.decrypt(value.encode()).decode()
        except Exception:
            logger.warning(
                "Failed to decrypt stored admin secret; it may need re-entry"
            )
            return ""

    @staticmethod
    def _build_llm_fingerprint(
        *,
        source: str,
        api_key: str,
        model: str,
        base_url: str,
        user_id: int,
        shared_config_id: int | None = None,
    ) -> str:
        normalized_base = _normalize_base_url(base_url)
        if source == "template":
            return f"template:{user_id}"

        owner = (
            f"shared:{shared_config_id}"
            if shared_config_id is not None
            else f"user:{user_id}"
        )
        digest = (
            hashlib.sha256(api_key.encode("utf-8")).hexdigest()[:16]
            if api_key
            else "none"
        )
        return f"{source}:{owner}:{digest}:{model}:{normalized_base}"

    # ── User CRUD ───────────────────────────────────────────────

    def create_user(
        self,
        username: str,
        password: str,
        display_name: str = "",
        is_admin: bool = False,
    ) -> int:
        now = _utc_now_iso()
        password_hash = bcrypt.hashpw(
            password.encode("utf-8"), bcrypt.gensalt()
        ).decode("utf-8")
        with self._get_conn() as conn:
            cursor = conn.execute(
                """INSERT INTO users
                   (username, password_hash, display_name, is_admin, created_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                (
                    username,
                    password_hash,
                    display_name or username,
                    int(is_admin),
                    now,
                    now,
                ),
            )
            return cursor.lastrowid or 0

    def get_user_by_username(self, username: str) -> Optional[dict[str, Any]]:
        with self._get_conn() as conn:
            row = conn.execute(
                "SELECT * FROM users WHERE username = ?", (username,)
            ).fetchone()
        return dict(row) if row else None

    def get_user_by_id(self, user_id: int) -> Optional[dict[str, Any]]:
        with self._get_conn() as conn:
            row = conn.execute(
                "SELECT * FROM users WHERE id = ?", (user_id,)
            ).fetchone()
        return dict(row) if row else None

    def list_users(self) -> list[dict[str, Any]]:
        with self._get_conn() as conn:
            rows = conn.execute(
                "SELECT id, username, display_name, is_admin, is_active, created_at FROM users ORDER BY id"
            ).fetchall()
        users = [dict(r) for r in rows]
        for user in users:
            user["group_ids"] = [g["id"] for g in self.list_groups_for_user(user["id"])]
            assignment = self.get_user_llm_assignment(user["id"])
            user["shared_llm_config_id"] = (
                assignment.get("llm_config_id") if assignment else None
            )
        return users

    def verify_password(self, username: str, password: str) -> Optional[dict[str, Any]]:
        user = self.get_user_by_username(username)
        if not user or not user.get("is_active"):
            return None
        if bcrypt.checkpw(
            password.encode("utf-8"), user["password_hash"].encode("utf-8")
        ):
            return user
        return None

    def update_password(self, user_id: int, new_password: str) -> None:
        now = _utc_now_iso()
        password_hash = bcrypt.hashpw(
            new_password.encode("utf-8"), bcrypt.gensalt()
        ).decode("utf-8")
        with self._get_conn() as conn:
            conn.execute(
                "UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?",
                (password_hash, now, user_id),
            )

    def update_profile(
        self,
        user_id: int,
        display_name: str | None = None,
        preferences: dict[str, Any] | None = None,
    ) -> None:
        now = _utc_now_iso()
        updates = ["updated_at = ?"]
        params: list[Any] = [now]
        if display_name is not None:
            updates.append("display_name = ?")
            params.append(display_name)
        if preferences is not None:
            updates.append("preferences = ?")
            params.append(json.dumps(preferences))
        params.append(user_id)
        with self._get_conn() as conn:
            conn.execute(f"UPDATE users SET {', '.join(updates)} WHERE id = ?", params)

    def count_admin_users(self) -> int:
        with self._get_conn() as conn:
            row = conn.execute(
                "SELECT COUNT(*) AS count FROM users WHERE is_admin = 1 AND is_active = 1"
            ).fetchone()
        return int(row["count"] if row else 0)

    def update_user(
        self,
        user_id: int,
        *,
        display_name: str,
        is_admin: bool,
        is_active: bool,
    ) -> None:
        now = _utc_now_iso()
        with self._get_conn() as conn:
            conn.execute(
                """UPDATE users
                   SET display_name = ?, is_admin = ?, is_active = ?, updated_at = ?
                   WHERE id = ?""",
                (display_name, int(is_admin), int(is_active), now, user_id),
            )

    def delete_user(self, user_id: int) -> None:
        with self._get_conn() as conn:
            conn.execute("DELETE FROM users WHERE id = ?", (user_id,))

    # ── Groups & Permissions ────────────────────────────────────

    def list_groups(self) -> list[dict[str, Any]]:
        with self._get_conn() as conn:
            rows = conn.execute(
                "SELECT id, name, description, priority, created_at FROM policy_groups ORDER BY priority, name"
            ).fetchall()
        groups = [dict(r) for r in rows]
        for group in groups:
            group["llm_config_id"] = self.get_group_llm_assignment(group["id"])
            group["plugin_permissions"] = self.list_group_plugin_permissions(
                group["id"]
            )
        return groups

    def create_group(
        self, name: str, description: str = "", priority: int = 100
    ) -> int:
        now = _utc_now_iso()
        with self._get_conn() as conn:
            cursor = conn.execute(
                """INSERT INTO policy_groups (name, description, priority, created_at, updated_at)
                   VALUES (?, ?, ?, ?, ?)""",
                (name, description, priority, now, now),
            )
            return cursor.lastrowid or 0

    def delete_group(self, group_id: int) -> None:
        with self._get_conn() as conn:
            conn.execute("DELETE FROM policy_groups WHERE id = ?", (group_id,))

    def update_group(
        self, group_id: int, name: str, description: str, priority: int
    ) -> None:
        with self._get_conn() as conn:
            conn.execute(
                "UPDATE policy_groups SET name = ?, description = ?, priority = ?, updated_at = ? WHERE id = ?",
                (name, description, priority, _utc_now_iso(), group_id),
            )

    def list_groups_for_user(self, user_id: int) -> list[dict[str, Any]]:
        with self._get_conn() as conn:
            rows = conn.execute(
                """SELECT g.* FROM policy_groups g
                   JOIN policy_group_members m ON m.group_id = g.id
                   WHERE m.user_id = ?
                   ORDER BY g.priority, g.name""",
                (user_id,),
            ).fetchall()
        return [dict(r) for r in rows]

    def set_user_groups(self, user_id: int, group_ids: list[int]) -> None:
        now = _utc_now_iso()
        normalized = sorted({gid for gid in group_ids if gid > 0})
        with self._get_conn() as conn:
            conn.execute(
                "DELETE FROM policy_group_members WHERE user_id = ?", (user_id,)
            )
            for group_id in normalized:
                conn.execute(
                    "INSERT INTO policy_group_members (user_id, group_id, created_at) VALUES (?, ?, ?)",
                    (user_id, group_id, now),
                )

    def set_user_plugin_permission(
        self, user_id: int, plugin_name: str, allowed: bool | None
    ) -> None:
        with self._get_conn() as conn:
            if allowed is None:
                conn.execute(
                    "DELETE FROM user_plugin_permissions WHERE user_id = ? AND plugin_name = ?",
                    (user_id, plugin_name),
                )
            else:
                conn.execute(
                    """INSERT INTO user_plugin_permissions (user_id, plugin_name, can_use_shared, updated_at)
                       VALUES (?, ?, ?, ?)
                       ON CONFLICT(user_id, plugin_name) DO UPDATE SET
                       can_use_shared = excluded.can_use_shared,
                       updated_at = excluded.updated_at""",
                    (user_id, plugin_name, int(allowed), _utc_now_iso()),
                )

    def list_user_plugin_permissions(self, user_id: int) -> dict[str, bool]:
        with self._get_conn() as conn:
            rows = conn.execute(
                "SELECT plugin_name, can_use_shared FROM user_plugin_permissions WHERE user_id = ?",
                (user_id,),
            ).fetchall()
        return {row["plugin_name"]: bool(row["can_use_shared"]) for row in rows}

    def set_group_plugin_permission(
        self, group_id: int, plugin_name: str, allowed: bool | None
    ) -> None:
        with self._get_conn() as conn:
            if allowed is None:
                conn.execute(
                    "DELETE FROM group_plugin_permissions WHERE group_id = ? AND plugin_name = ?",
                    (group_id, plugin_name),
                )
            else:
                conn.execute(
                    """INSERT INTO group_plugin_permissions (group_id, plugin_name, can_use_shared, updated_at)
                       VALUES (?, ?, ?, ?)
                       ON CONFLICT(group_id, plugin_name) DO UPDATE SET
                       can_use_shared = excluded.can_use_shared,
                       updated_at = excluded.updated_at""",
                    (group_id, plugin_name, int(allowed), _utc_now_iso()),
                )

    def list_group_plugin_permissions(self, group_id: int) -> dict[str, bool]:
        with self._get_conn() as conn:
            rows = conn.execute(
                "SELECT plugin_name, can_use_shared FROM group_plugin_permissions WHERE group_id = ?",
                (group_id,),
            ).fetchall()
        return {row["plugin_name"]: bool(row["can_use_shared"]) for row in rows}

    def can_user_use_shared_plugin(self, user_id: int, plugin_name: str) -> bool:
        direct = self.list_user_plugin_permissions(user_id)
        if plugin_name in direct:
            return direct[plugin_name]

        group_rows = self.list_groups_for_user(user_id)
        group_ids = [group["id"] for group in group_rows]
        if group_ids:
            permissions = [
                self.list_group_plugin_permissions(group_id) for group_id in group_ids
            ]
            if any(
                plugin_name in perm and not perm[plugin_name] for perm in permissions
            ):
                return False
            if any(plugin_name in perm and perm[plugin_name] for perm in permissions):
                return True

        from storage.result_store import get_result_store

        return get_result_store().is_shared_keys_allowed()

    # ── Personal & Shared LLM Settings ──────────────────────────

    def resolve_effective_llm_access(self, user_id: int) -> dict[str, Any]:
        user = self.get_user_by_id(user_id)
        can_use_personal = self.can_user_use_personal_llm(user_id)

        with self._get_conn() as conn:
            personal_row = conn.execute(
                "SELECT * FROM llm_settings WHERE user_id = ?", (user_id,)
            ).fetchone()

        personal_api_key = ""
        personal_model = ""
        personal_base_url = ""
        if personal_row:
            legacy_plain = personal_row["api_key"] or ""
            encrypted = personal_row["encrypted_api_key"] or ""
            personal_api_key = (
                self._decrypt_secret(encrypted) if encrypted else legacy_plain
            ).strip()
            personal_model = personal_row["model"] or ""
            personal_base_url = _normalize_base_url(personal_row["base_url"] or "")

        active_shared_configs = {
            config["id"]: config
            for config in self.list_shared_llm_configs()
            if config.get("is_active")
        }
        has_explicit_shared_policy = bool(self.list_user_llm_allowlist(user_id)) or any(
            self.list_group_llm_allowlist(group["id"])
            for group in self.list_groups_for_user(user_id)
        )
        allowed_shared_config_ids: list[int] = []
        if user and user.get("is_admin"):
            allowed_shared_config_ids = sorted(active_shared_configs)
        else:
            explicitly_allowed = [
                config_id
                for config_id in sorted(active_shared_configs)
                if self.can_user_use_shared_llm(user_id, config_id)
            ]
            if explicitly_allowed or has_explicit_shared_policy:
                allowed_shared_config_ids = explicitly_allowed
            else:
                direct_assignment = self.get_user_llm_assignment(user_id)
                if (
                    direct_assignment
                    and direct_assignment.get("llm_config_id") in active_shared_configs
                ):
                    allowed_shared_config_ids.append(
                        int(direct_assignment["llm_config_id"])
                    )

                for group in self.list_groups_for_user(user_id):
                    group_config_id = self.get_group_llm_assignment(group["id"])
                    if (
                        group_config_id is not None
                        and group_config_id in active_shared_configs
                    ):
                        allowed_shared_config_ids.append(group_config_id)

                allowed_shared_config_ids = sorted(set(allowed_shared_config_ids))

        assigned = self.get_assigned_shared_llm(user_id)
        if (
            assigned
            and assigned.get("shared_config_id") not in allowed_shared_config_ids
        ):
            assigned = None
        selected_shared_config_id = (
            int(assigned["shared_config_id"])
            if assigned and assigned.get("shared_config_id") is not None
            else None
        )
        selected_shared_config_name = (
            str(assigned.get("shared_config_name", "")) if assigned else None
        )

        from storage.result_store import get_result_store
        from app.config import settings

        configured_only = get_result_store().is_configured_only()
        env_api_key = (settings.llm_api_key or "").strip()
        env_model = settings.llm_model or "gpt-4o"
        env_base_url = _normalize_base_url(settings.llm_base_url or "")

        if personal_api_key and ((user and user.get("is_admin")) or can_use_personal):
            return {
                "user_id": user_id,
                "api_key": personal_api_key,
                "model": personal_model,
                "base_url": personal_base_url,
                "source": "personal",
                "fingerprint": self._build_llm_fingerprint(
                    source="personal",
                    api_key=personal_api_key,
                    model=personal_model,
                    base_url=personal_base_url,
                    user_id=user_id,
                ),
                "can_use_personal_llm": can_use_personal,
                "can_use_shared_llm": bool(allowed_shared_config_ids),
                "configured_only": configured_only,
                "allow_template_fallback": True,
                "allowed_shared_config_ids": allowed_shared_config_ids,
                "selected_shared_config_id": selected_shared_config_id,
                "selected_shared_config_name": selected_shared_config_name,
            }

        if assigned:
            shared_api_key = (assigned.get("api_key") or "").strip()
            shared_model = assigned.get("model") or ""
            shared_base_url = _normalize_base_url(assigned.get("base_url") or "")
            shared_source = assigned.get("source", "shared")
            return {
                "user_id": user_id,
                "api_key": shared_api_key,
                "model": shared_model,
                "base_url": shared_base_url,
                "source": shared_source,
                "shared_config_id": selected_shared_config_id,
                "shared_config_name": selected_shared_config_name,
                "fingerprint": self._build_llm_fingerprint(
                    source=shared_source,
                    api_key=shared_api_key,
                    model=shared_model,
                    base_url=shared_base_url,
                    user_id=user_id,
                    shared_config_id=selected_shared_config_id,
                ),
                "can_use_personal_llm": can_use_personal,
                "can_use_shared_llm": bool(allowed_shared_config_ids),
                "configured_only": configured_only,
                "allow_template_fallback": True,
                "allowed_shared_config_ids": allowed_shared_config_ids,
                "selected_shared_config_id": selected_shared_config_id,
                "selected_shared_config_name": selected_shared_config_name,
            }

        if env_api_key and not configured_only:
            return {
                "user_id": user_id,
                "api_key": env_api_key,
                "model": env_model,
                "base_url": env_base_url,
                "source": "env",
                "fingerprint": self._build_llm_fingerprint(
                    source="env",
                    api_key=env_api_key,
                    model=env_model,
                    base_url=env_base_url,
                    user_id=user_id,
                ),
                "can_use_personal_llm": can_use_personal,
                "can_use_shared_llm": bool(allowed_shared_config_ids),
                "configured_only": configured_only,
                "allow_template_fallback": True,
                "allowed_shared_config_ids": allowed_shared_config_ids,
                "selected_shared_config_id": selected_shared_config_id,
                "selected_shared_config_name": selected_shared_config_name,
            }

        return {
            "user_id": user_id,
            "api_key": "",
            "model": "",
            "base_url": "",
            "source": "template",
            "fingerprint": self._build_llm_fingerprint(
                source="template",
                api_key="",
                model="",
                base_url="",
                user_id=user_id,
            ),
            "can_use_personal_llm": can_use_personal,
            "can_use_shared_llm": bool(allowed_shared_config_ids),
            "configured_only": configured_only,
            "allow_template_fallback": True,
            "allowed_shared_config_ids": allowed_shared_config_ids,
            "selected_shared_config_id": selected_shared_config_id,
            "selected_shared_config_name": selected_shared_config_name,
        }

    def get_llm_settings(self, user_id: int) -> dict[str, Any]:
        return self.resolve_effective_llm_access(user_id)

    def save_llm_settings(
        self,
        user_id: int,
        api_key: str = "",
        model: str = "gpt-4o",
        base_url: str = "https://api.openai.com/v1",
    ) -> None:
        now = _utc_now_iso()
        encrypted = self._encrypt_secret(api_key.strip())
        with self._get_conn() as conn:
            conn.execute(
                """INSERT INTO llm_settings (user_id, api_key, encrypted_api_key, model, base_url, updated_at)
                   VALUES (?, '', ?, ?, ?, ?)
                   ON CONFLICT(user_id) DO UPDATE SET
                     api_key = '',
                     encrypted_api_key = excluded.encrypted_api_key,
                     model = excluded.model,
                     base_url = excluded.base_url,
                     updated_at = excluded.updated_at""",
                (user_id, encrypted, model, base_url, now),
            )

    def get_llm_policy(self) -> dict[str, Any]:
        with self._get_conn() as conn:
            row = conn.execute(
                "SELECT allow_personal_llm_default, allow_shared_llm_default FROM llm_policy ORDER BY id DESC LIMIT 1"
            ).fetchone()
        return {
            "allow_personal_llm_default": bool(row["allow_personal_llm_default"])
            if row
            else False,
            "allow_shared_llm_default": bool(row["allow_shared_llm_default"])
            if row
            else False,
        }

    def set_llm_policy(
        self,
        allow_personal_llm_default: bool,
        allow_shared_llm_default: bool | None = None,
    ) -> None:
        with self._get_conn() as conn:
            current = self.get_llm_policy()
            conn.execute(
                "UPDATE llm_policy SET allow_personal_llm_default = ?, allow_shared_llm_default = ?, updated_at = ? WHERE id = (SELECT id FROM llm_policy ORDER BY id DESC LIMIT 1)",
                (
                    int(allow_personal_llm_default),
                    int(
                        current["allow_shared_llm_default"]
                        if allow_shared_llm_default is None
                        else allow_shared_llm_default
                    ),
                    _utc_now_iso(),
                ),
            )

    def set_user_personal_llm_permission(
        self, user_id: int, allowed: bool | None
    ) -> None:
        with self._get_conn() as conn:
            if allowed is None:
                conn.execute(
                    "DELETE FROM user_llm_permissions WHERE user_id = ?", (user_id,)
                )
            else:
                conn.execute(
                    """INSERT INTO user_llm_permissions (user_id, allow_personal_llm, updated_at)
                       VALUES (?, ?, ?)
                       ON CONFLICT(user_id) DO UPDATE SET
                         allow_personal_llm = excluded.allow_personal_llm,
                         updated_at = excluded.updated_at""",
                    (user_id, int(allowed), _utc_now_iso()),
                )

    def get_user_personal_llm_permission(self, user_id: int) -> bool | None:
        with self._get_conn() as conn:
            row = conn.execute(
                "SELECT allow_personal_llm FROM user_llm_permissions WHERE user_id = ?",
                (user_id,),
            ).fetchone()
        if row is None:
            return None
        return bool(row["allow_personal_llm"])

    def set_group_personal_llm_permission(
        self, group_id: int, allowed: bool | None
    ) -> None:
        with self._get_conn() as conn:
            if allowed is None:
                conn.execute(
                    "DELETE FROM group_llm_permissions WHERE group_id = ?", (group_id,)
                )
            else:
                conn.execute(
                    """INSERT INTO group_llm_permissions (group_id, allow_personal_llm, updated_at)
                       VALUES (?, ?, ?)
                       ON CONFLICT(group_id) DO UPDATE SET
                         allow_personal_llm = excluded.allow_personal_llm,
                         updated_at = excluded.updated_at""",
                    (group_id, int(allowed), _utc_now_iso()),
                )

    def get_group_personal_llm_permission(self, group_id: int) -> bool | None:
        with self._get_conn() as conn:
            row = conn.execute(
                "SELECT allow_personal_llm FROM group_llm_permissions WHERE group_id = ?",
                (group_id,),
            ).fetchone()
        if row is None:
            return None
        return bool(row["allow_personal_llm"])

    def can_user_use_personal_llm(self, user_id: int) -> bool:
        user = self.get_user_by_id(user_id)
        if user and user.get("is_admin"):
            return True

        direct = self.get_user_personal_llm_permission(user_id)
        if direct is not None:
            return direct

        groups = self.list_groups_for_user(user_id)
        if groups:
            decisions = [
                self.get_group_personal_llm_permission(group["id"]) for group in groups
            ]
            if any(value is False for value in decisions):
                return False
            if any(value is True for value in decisions):
                return True

        return self.get_llm_policy()["allow_personal_llm_default"]

    def set_user_llm_allowlist(
        self, user_id: int, llm_config_id: int, allowed: bool | None
    ) -> None:
        with self._get_conn() as conn:
            if allowed is None:
                conn.execute(
                    "DELETE FROM user_llm_allowlist WHERE user_id = ? AND llm_config_id = ?",
                    (user_id, llm_config_id),
                )
            else:
                conn.execute(
                    """INSERT INTO user_llm_allowlist (user_id, llm_config_id, allowed, updated_at)
                       VALUES (?, ?, ?, ?)
                       ON CONFLICT(user_id, llm_config_id) DO UPDATE SET
                         allowed = excluded.allowed,
                         updated_at = excluded.updated_at""",
                    (user_id, llm_config_id, int(allowed), _utc_now_iso()),
                )

    def list_user_llm_allowlist(self, user_id: int) -> dict[int, bool]:
        with self._get_conn() as conn:
            rows = conn.execute(
                "SELECT llm_config_id, allowed FROM user_llm_allowlist WHERE user_id = ?",
                (user_id,),
            ).fetchall()
        return {int(row["llm_config_id"]): bool(row["allowed"]) for row in rows}

    def set_group_llm_allowlist(
        self, group_id: int, llm_config_id: int, allowed: bool | None
    ) -> None:
        with self._get_conn() as conn:
            if allowed is None:
                conn.execute(
                    "DELETE FROM group_llm_allowlist WHERE group_id = ? AND llm_config_id = ?",
                    (group_id, llm_config_id),
                )
            else:
                conn.execute(
                    """INSERT INTO group_llm_allowlist (group_id, llm_config_id, allowed, updated_at)
                       VALUES (?, ?, ?, ?)
                       ON CONFLICT(group_id, llm_config_id) DO UPDATE SET
                         allowed = excluded.allowed,
                         updated_at = excluded.updated_at""",
                    (group_id, llm_config_id, int(allowed), _utc_now_iso()),
                )

    def list_group_llm_allowlist(self, group_id: int) -> dict[int, bool]:
        with self._get_conn() as conn:
            rows = conn.execute(
                "SELECT llm_config_id, allowed FROM group_llm_allowlist WHERE group_id = ?",
                (group_id,),
            ).fetchall()
        return {int(row["llm_config_id"]): bool(row["allowed"]) for row in rows}

    def can_user_use_shared_llm(self, user_id: int, llm_config_id: int) -> bool:
        user = self.get_user_by_id(user_id)
        if user and user.get("is_admin"):
            return True

        user_allowlist = self.list_user_llm_allowlist(user_id)
        direct = user_allowlist.get(llm_config_id)
        if direct is not None:
            return direct

        groups = self.list_groups_for_user(user_id)
        group_allowlists = [
            self.list_group_llm_allowlist(group["id"]) for group in groups
        ]
        decisions = [allowlist.get(llm_config_id) for allowlist in group_allowlists]
        if any(value is False for value in decisions):
            return False
        if any(value is True for value in decisions):
            return True

        if user_allowlist or any(allowlist for allowlist in group_allowlists):
            return self.get_llm_policy()["allow_shared_llm_default"]

        # Legacy fallback: previous deployments treated assignment as effective access.
        assignment = self.get_user_llm_assignment(user_id)
        if assignment and assignment.get("llm_config_id") == llm_config_id:
            return True
        if any(
            self.get_group_llm_assignment(group["id"]) == llm_config_id
            for group in groups
        ):
            return True

        return self.get_llm_policy()["allow_shared_llm_default"]

    def list_shared_llm_configs(self) -> list[dict[str, Any]]:
        with self._get_conn() as conn:
            rows = conn.execute(
                "SELECT id, name, model, base_url, is_active, created_at, updated_at FROM shared_llm_configs ORDER BY id"
            ).fetchall()
        return [dict(r) for r in rows]

    def save_shared_llm_config(
        self,
        name: str,
        api_key: str,
        model: str,
        base_url: str,
        *,
        config_id: int | None = None,
        is_active: bool = True,
    ) -> None:
        now = _utc_now_iso()
        encrypted = self._encrypt_secret(api_key.strip())
        with self._get_conn() as conn:
            if config_id is None:
                conn.execute(
                    """INSERT INTO shared_llm_configs
                       (name, encrypted_api_key, model, base_url, is_active, created_at, updated_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?)""",
                    (name, encrypted, model, base_url, int(is_active), now, now),
                )
            else:
                if encrypted:
                    conn.execute(
                        """UPDATE shared_llm_configs
                           SET name = ?, encrypted_api_key = ?, model = ?, base_url = ?,
                               is_active = ?, updated_at = ?
                           WHERE id = ?""",
                        (
                            name,
                            encrypted,
                            model,
                            base_url,
                            int(is_active),
                            now,
                            config_id,
                        ),
                    )
                else:
                    conn.execute(
                        """UPDATE shared_llm_configs
                           SET name = ?, model = ?, base_url = ?, is_active = ?, updated_at = ?
                           WHERE id = ?""",
                        (name, model, base_url, int(is_active), now, config_id),
                    )

    def delete_shared_llm_config(self, config_id: int) -> None:
        with self._get_conn() as conn:
            conn.execute("DELETE FROM shared_llm_configs WHERE id = ?", (config_id,))

    def assign_shared_llm_to_user(
        self, user_id: int, llm_config_id: int | None
    ) -> None:
        with self._get_conn() as conn:
            conn.execute(
                """INSERT INTO user_llm_assignments (user_id, llm_config_id, updated_at)
                   VALUES (?, ?, ?)
                   ON CONFLICT(user_id) DO UPDATE SET
                     llm_config_id = excluded.llm_config_id,
                     updated_at = excluded.updated_at""",
                (user_id, llm_config_id, _utc_now_iso()),
            )

    def assign_shared_llm_to_group(
        self, group_id: int, llm_config_id: int | None
    ) -> None:
        with self._get_conn() as conn:
            conn.execute(
                """INSERT INTO group_llm_assignments (group_id, llm_config_id, updated_at)
                   VALUES (?, ?, ?)
                   ON CONFLICT(group_id) DO UPDATE SET
                     llm_config_id = excluded.llm_config_id,
                     updated_at = excluded.updated_at""",
                (group_id, llm_config_id, _utc_now_iso()),
            )

    def get_user_llm_assignment(self, user_id: int) -> Optional[dict[str, Any]]:
        with self._get_conn() as conn:
            row = conn.execute(
                "SELECT user_id, llm_config_id FROM user_llm_assignments WHERE user_id = ?",
                (user_id,),
            ).fetchone()
        return dict(row) if row else None

    def get_group_llm_assignment(self, group_id: int) -> int | None:
        with self._get_conn() as conn:
            row = conn.execute(
                "SELECT llm_config_id FROM group_llm_assignments WHERE group_id = ?",
                (group_id,),
            ).fetchone()
        return (
            int(row["llm_config_id"])
            if row and row["llm_config_id"] is not None
            else None
        )

    def get_assigned_shared_llm(self, user_id: int) -> Optional[dict[str, Any]]:
        with self._get_conn() as conn:
            direct = conn.execute(
                """SELECT c.* FROM shared_llm_configs c
                   JOIN user_llm_assignments a ON a.llm_config_id = c.id
                   WHERE a.user_id = ? AND c.is_active = 1""",
                (user_id,),
            ).fetchone()
            if direct:
                api_key = self._decrypt_secret(direct["encrypted_api_key"])
                return {
                    "user_id": user_id,
                    "api_key": api_key,
                    "model": direct["model"],
                    "base_url": _normalize_base_url(direct["base_url"]),
                    "source": "shared-user",
                    "shared_config_id": direct["id"],
                    "shared_config_name": direct["name"],
                    "fingerprint": self._build_llm_fingerprint(
                        source="shared-user",
                        api_key=api_key,
                        model=direct["model"],
                        base_url=direct["base_url"],
                        user_id=user_id,
                        shared_config_id=int(direct["id"]),
                    ),
                }

            group_row = conn.execute(
                """SELECT c.*, g.id AS group_id, g.name AS group_name, g.priority AS group_priority
                   FROM shared_llm_configs c
                   JOIN group_llm_assignments a ON a.llm_config_id = c.id
                   JOIN policy_groups g ON g.id = a.group_id
                   JOIN policy_group_members m ON m.group_id = g.id
                   WHERE m.user_id = ? AND c.is_active = 1
                   ORDER BY g.priority ASC, g.id ASC
                   LIMIT 1""",
                (user_id,),
            ).fetchone()

        if group_row:
            api_key = self._decrypt_secret(group_row["encrypted_api_key"])
            return {
                "user_id": user_id,
                "api_key": api_key,
                "model": group_row["model"],
                "base_url": _normalize_base_url(group_row["base_url"]),
                "source": "shared-group",
                "shared_config_id": group_row["id"],
                "shared_config_name": group_row["name"],
                "group_id": group_row["group_id"],
                "group_name": group_row["group_name"],
                "fingerprint": self._build_llm_fingerprint(
                    source="shared-group",
                    api_key=api_key,
                    model=group_row["model"],
                    base_url=group_row["base_url"],
                    user_id=user_id,
                    shared_config_id=int(group_row["id"]),
                ),
            }
        return None

    # ── Audit Log ───────────────────────────────────────────────

    def log_action(self, user_id: int | None, action: str, detail: str = "") -> None:
        with self._get_conn() as conn:
            conn.execute(
                "INSERT INTO audit_log (user_id, action, detail, created_at) VALUES (?, ?, ?, ?)",
                (user_id, action, detail, _utc_now_iso()),
            )

    def get_recent_logs(self, limit: int = 50) -> list[dict[str, Any]]:
        with self._get_conn() as conn:
            rows = conn.execute(
                """SELECT a.*, u.username FROM audit_log a
                   LEFT JOIN users u ON a.user_id = u.id
                   ORDER BY a.id DESC LIMIT ?""",
                (limit,),
            ).fetchall()
        return [dict(r) for r in rows]

    # ── Bootstrap ───────────────────────────────────────────────

    def ensure_admin_exists(self) -> None:
        default_pw = os.environ.get("ADMIN_PASSWORD", "admin")
        now = _utc_now_iso()
        password_hash = bcrypt.hashpw(
            default_pw.encode("utf-8"), bcrypt.gensalt()
        ).decode("utf-8")
        with self._get_conn() as conn:
            cursor = conn.execute(
                """INSERT INTO users
                   (username, password_hash, display_name, is_admin, created_at, updated_at)
                   SELECT ?, ?, ?, ?, ?, ?
                   WHERE NOT EXISTS (SELECT 1 FROM users)""",
                ("admin", password_hash, "Administrator", 1, now, now),
            )
        if cursor.rowcount:
            logger.info(
                "Created default admin user (username: admin). Change the password after first login!"
            )


admin_db = AdminDB()
