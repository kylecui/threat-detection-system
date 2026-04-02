"""Helper functions for API report and snapshot flows."""

from __future__ import annotations

import json
import logging
from datetime import datetime
from typing import Any, Awaitable, Callable

from fastapi import HTTPException, Request

from app.config import settings
from app.i18n import i18n
from models import Verdict
from storage.result_store import get_result_store

logger = logging.getLogger(__name__)


def get_lang(request: Request) -> str:
    lang = request.query_params.get("lang")
    if lang in i18n.SUPPORTED_LANGS:
        return lang
    cookie_lang = request.cookies.get("preferred_locale")
    if cookie_lang in i18n.SUPPORTED_LANGS:
        return cookie_lang
    return settings.language


def can_view_snapshot(user: dict[str, Any] | None, snapshot: dict[str, Any]) -> bool:
    if snapshot.get("api_key_type") == "shared":
        return True
    if not user:
        return False
    if user.get("is_admin"):
        return True
    return snapshot.get("user_id") == user.get("id")


def can_view_report(user: dict[str, Any] | None, report: dict[str, Any]) -> bool:
    if not user:
        return False
    if user.get("is_admin"):
        return True
    return report.get("user_id") == user.get("id")


def get_latest_visible_snapshot(ip: str, user_id: int) -> dict[str, Any] | None:
    personal_snapshot = get_result_store().get_latest_snapshot(
        ip=ip,
        user_id=user_id,
        api_key_type="personal",
    )
    shared_snapshot = get_result_store().get_latest_snapshot(ip=ip)
    candidates = [s for s in [personal_snapshot, shared_snapshot] if s]
    if not candidates:
        return None
    return max(candidates, key=lambda snapshot: snapshot.get("queried_at", ""))


def load_latest_report_verdict(
    *,
    ip: str,
    user_id: int,
    cached_verdict_loader: Callable[[str], Any | None],
) -> tuple[Any | None, dict[str, Any] | None]:
    latest_snapshot = get_latest_visible_snapshot(ip, user_id)
    if latest_snapshot and latest_snapshot.get("verdict_json"):
        try:
            return Verdict.model_validate(
                json.loads(latest_snapshot["verdict_json"])
            ), latest_snapshot
        except Exception as exc:
            logger.warning(
                "Failed to load persisted snapshot verdict for %s (snapshot=%s): %s",
                ip,
                latest_snapshot.get("id"),
                exc,
            )
    return cached_verdict_loader(ip), latest_snapshot


def recent_dashboard_context(user_id: int) -> dict[str, Any]:
    return {
        "recent_queries": get_result_store().get_user_snapshot_history(
            user_id, limit=10
        ),
        "recent_reports": get_result_store().get_user_report_history(user_id, limit=10),
    }


def resolve_cached_report(
    *,
    ip: str,
    user_id: int,
    llm_fingerprint: str,
    lang: str,
    snapshot_id: int | None,
) -> dict[str, Any] | None:
    return get_result_store().get_latest_report(
        ip=ip,
        user_id=user_id,
        llm_fingerprint=llm_fingerprint,
        lang=lang,
        snapshot_id=snapshot_id,
    )


def archive_reports_for_user(ip: str, user_id: int) -> int:
    return get_result_store().archive_reports(ip=ip, user_id=user_id)


def parse_snapshot_query_date(
    latest_snapshot: dict[str, Any] | None,
) -> datetime | None:
    if not latest_snapshot or not latest_snapshot.get("queried_at"):
        return None
    try:
        return datetime.fromisoformat(latest_snapshot["queried_at"])
    except (ValueError, TypeError):
        return None


async def render_and_persist_report(
    *,
    ip: str,
    user_id: int,
    report_verdict: Verdict,
    lang: str,
    llm_settings: dict[str, Any],
    snapshot_id: int | None,
    query_date: datetime | None,
    report_renderer: Callable[..., Awaitable[tuple[str, bool, bool]]],
) -> tuple[str, bool, bool]:
    html, llm_used, llm_fallback = await report_renderer(
        report_verdict,
        lang=lang,
        llm_overrides=llm_settings,
        query_date=query_date,
    )
    get_result_store().save_report(
        ip=ip,
        user_id=user_id,
        report_html=html,
        llm_enhanced=llm_used,
        llm_fingerprint=llm_settings.get("fingerprint", ""),
        llm_source=llm_settings.get("source", "template"),
        lang=lang,
        snapshot_id=snapshot_id,
    )
    return html, llm_used, llm_fallback


def load_and_authorize_snapshot(
    snapshot_id: int, user: dict[str, Any] | None
) -> dict[str, Any]:
    snapshot = get_result_store().get_snapshot_by_id(snapshot_id)
    if not snapshot:
        raise HTTPException(status_code=404, detail=f"Snapshot {snapshot_id} not found")
    if not can_view_snapshot(user, snapshot):
        raise HTTPException(status_code=403, detail="Access denied")
    return snapshot


def build_timeline_from_snapshots(
    snapshots: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    return [
        {
            "id": s["id"],
            "queried_at": s["queried_at"],
            "final_score": s["final_score"],
            "level": s["level"],
            "is_archived": s.get("is_archived", 0),
        }
        for s in reversed(snapshots)
    ]
