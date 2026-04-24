"""
FastAPI interface for Threat Intelligence Reasoning Engine.
"""

# pyright: reportMissingImports=false, reportImplicitRelativeImport=false, reportMissingModuleSource=false

import logging
import os
import json
import time
from contextlib import asynccontextmanager
from datetime import datetime
from app.config import settings

# Configure logging BEFORE anything else imports loggers
logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)

from fastapi import FastAPI, HTTPException, Request, Form, Response
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.templating import Jinja2Templates
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest
from starlette.middleware.sessions import SessionMiddleware
from typing import Any, Optional
from app.service import ThreatIntelService
from app.i18n import i18n
from models import ContextProfile, Verdict
from reporters.json_reporter import JSONReporter
from reporters.html_reporter import HTMLReporter
from reporters.narrative_reporter import NarrativeReporter
from admin.routes import router as admin_router
from admin.database import admin_db
from admin.auth import get_current_user, login_redirect
from app import api_helpers
from storage.result_store import get_result_store

logger = logging.getLogger(__name__)


TIRE_LOOKUPS_TOTAL = Counter(
    "tire_lookups_total",
    "Total TIRE lookups by verdict",
    ["verdict"],
)
TIRE_LOOKUP_DURATION_SECONDS = Histogram(
    "tire_lookup_duration_seconds",
    "TIRE lookup duration in seconds",
)
TIRE_PLUGIN_CALLS_TOTAL = Counter(
    "tire_plugin_calls_total",
    "Total TIRE plugin calls by plugin and status",
    ["plugin_name", "status"],
)


def _snake_to_camel(name: str) -> str:
    parts = name.split("_")
    return parts[0] + "".join(part.capitalize() for part in parts[1:])


def _camelize_json(value: Any) -> Any:
    if isinstance(value, dict):
        return {
            _snake_to_camel(key) if isinstance(key, str) else key: _camelize_json(val)
            for key, val in value.items()
        }
    if isinstance(value, list):
        return [_camelize_json(item) for item in value]
    return value


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize app-scoped services on startup."""
    admin_db.ensure_admin_exists()

    from admin.log_handler import MemoryLogHandler, LogStore

    store = LogStore()
    store.bind_loop()
    handler = MemoryLogHandler(store)
    handler.setFormatter(logging.Formatter("%(message)s"))
    logging.getLogger().addHandler(handler)
    yield


app = FastAPI(
    title="Threat Intelligence Reasoning Engine",
    description="Multi-source threat intelligence analysis and reasoning engine",
    version="2.0.0",
    root_path=settings.root_path,
    lifespan=lifespan,
)

# Session middleware for admin portal cookie-based auth
app.add_middleware(SessionMiddleware, secret_key=settings.session_secret_key)

# Admin portal routes
app.include_router(admin_router)

service = ThreatIntelService()
json_reporter = JSONReporter()
html_reporter = HTMLReporter()
narrative_reporter = NarrativeReporter()

templates = Jinja2Templates(
    directory=os.path.join(os.path.dirname(__file__), "..", "templates")
)


def _get_latest_visible_snapshot(ip: str, user_id: int) -> dict[str, Any] | None:
    """Return the latest visible snapshot across personal and shared scopes."""
    return api_helpers.get_latest_visible_snapshot(ip, user_id)


def _load_latest_report_verdict(
    ip: str, user_id: int
) -> tuple[Any | None, dict[str, Any] | None]:
    """Prefer the latest visible persisted snapshot verdict for report generation.

    Falls back to the in-memory/TTL cached verdict when no visible snapshot exists.
    Returns a tuple of (verdict, latest_snapshot).
    """
    return api_helpers.load_latest_report_verdict(
        ip=ip,
        user_id=user_id,
        cached_verdict_loader=service.query_engine.cache.get_verdict,
    )


def _recent_dashboard_context(user_id: int) -> dict[str, Any]:
    """Return recent dashboard history data for a user."""
    return api_helpers.recent_dashboard_context(user_id)


def _resolve_cached_report(
    *,
    ip: str,
    user_id: int,
    llm_fingerprint: str,
    lang: str,
    snapshot_id: int | None,
) -> dict[str, Any] | None:
    """Resolve the latest cached report matching the current rendering context."""
    return api_helpers.resolve_cached_report(
        ip=ip,
        user_id=user_id,
        llm_fingerprint=llm_fingerprint,
        lang=lang,
        snapshot_id=snapshot_id,
    )


def _archive_reports_for_user(ip: str, user_id: int) -> int:
    """Archive previous reports for a user when regenerating."""
    return api_helpers.archive_reports_for_user(ip, user_id)


def _parse_snapshot_query_date(
    latest_snapshot: dict[str, Any] | None,
) -> datetime | None:
    """Parse the snapshot queried_at timestamp when present."""
    return api_helpers.parse_snapshot_query_date(latest_snapshot)


async def _render_and_persist_report(
    *,
    ip: str,
    user_id: int,
    report_verdict: Verdict,
    lang: str,
    llm_settings: dict[str, Any],
    snapshot_id: int | None,
    query_date: datetime | None,
) -> tuple[str, bool, bool]:
    """Render a narrative report and persist it."""
    return await api_helpers.render_and_persist_report(
        ip=ip,
        user_id=user_id,
        report_verdict=report_verdict,
        lang=lang,
        snapshot_id=snapshot_id,
        llm_settings=llm_settings,
        query_date=query_date,
        report_renderer=narrative_reporter.generate,
    )


class AnalyzeRequest(BaseModel):
    """Request model for context-aware analysis."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    ip: str
    context: Optional[ContextProfile] = None
    refresh: bool = False


def _get_lang(request: Request) -> str:
    """Resolve display language: ?lang= query param → cookie → config default."""
    return api_helpers.get_lang(request)


def _can_view_snapshot(user: dict[str, Any] | None, snapshot: dict[str, Any]) -> bool:
    return api_helpers.can_view_snapshot(user, snapshot)


def _can_view_report(user: dict[str, Any] | None, report: dict[str, Any]) -> bool:
    return api_helpers.can_view_report(user, report)


def _load_and_authorize_snapshot(
    snapshot_id: int, user: dict[str, Any] | None
) -> dict[str, Any]:
    """Load a snapshot and enforce view permissions."""
    return api_helpers.load_and_authorize_snapshot(snapshot_id, user)


def _build_timeline_from_snapshots(
    snapshots: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """Build oldest-first timeline payload from visible snapshots."""
    return api_helpers.build_timeline_from_snapshots(snapshots)


@app.get("/healthz")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "service": "threat-intel-reasoning-engine"}


@app.get("/readyz")
async def readiness_check():
    """Readiness check endpoint."""
    return {"status": "ready", "service": "threat-intel-reasoning-engine"}


@app.get("/metrics")
async def metrics() -> Response:
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)


@app.get("/api/v1/ip/{ip}")
async def analyze_ip(ip: str, request: Request, refresh: bool = False):
    """Analyze an IP address for threats."""
    started_at = time.perf_counter()
    verdict: Verdict | None = None
    try:
        user = get_current_user(request)
        user_id = user["id"] if user else None
        verdict = await service.analyze_ip(ip, refresh=refresh, user_id=user_id)
        report = json_reporter.generate(verdict)

        # Parse back to dict for JSON response
        import json

        return JSONResponse(content=_camelize_json(json.loads(report)))

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Analysis failed: {str(e)}")
    finally:
        TIRE_LOOKUP_DURATION_SECONDS.observe(time.perf_counter() - started_at)
        if "verdict" in locals() and verdict is not None:
            TIRE_LOOKUPS_TOTAL.labels(
                verdict=getattr(verdict, "level", "unknown")
            ).inc()


@app.post("/api/v1/analyze/ip")
async def analyze_ip_with_context(request: Request, body: AnalyzeRequest):
    """Context-aware IP analysis."""
    started_at = time.perf_counter()
    verdict: Verdict | None = None
    try:
        user = get_current_user(request)
        user_id = user["id"] if user else None
        verdict = await service.analyze_ip(
            body.ip, body.context, body.refresh, user_id=user_id
        )
        report = json_reporter.generate(verdict)

        # Parse back to dict for JSON response
        import json

        return JSONResponse(content=_camelize_json(json.loads(report)))

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Analysis failed: {str(e)}")
    finally:
        TIRE_LOOKUP_DURATION_SECONDS.observe(time.perf_counter() - started_at)
        if "verdict" in locals() and verdict is not None:
            TIRE_LOOKUPS_TOTAL.labels(
                verdict=getattr(verdict, "level", "unknown")
            ).inc()


@app.get("/api/v1/docs")
async def api_docs():
    """Redirect to FastAPI docs."""
    # FastAPI automatically provides /docs endpoint
    pass


@app.get("/api/v1/results/{ip}/history")
async def get_result_history(ip: str, request: Request, limit: int = 20):
    """Return historical query snapshots for an IP.

    Supports the timeline comparison feature — shows score changes
    across multiple queries of the same IP over time.
    """
    from storage.result_store import result_store

    try:
        user = get_current_user(request)
        snapshots = result_store.get_visible_snapshot_history(
            ip=ip,
            user_id=user["id"] if user else None,
            is_admin=bool(user and user.get("is_admin")),
            limit=limit,
        )
        return JSONResponse(
            content=_camelize_json(
                {"ip": ip, "snapshots": snapshots, "count": len(snapshots)}
            )
        )
    except Exception as e:
        logger.error("Failed to fetch history for %s: %s", ip, e)
        raise HTTPException(
            status_code=500,
            detail=f"Failed to fetch result history: {str(e)}",
        )


@app.get("/api/v1/results/snapshot/{snapshot_id}")
async def get_snapshot_detail(snapshot_id: int, request: Request):
    """Return a single query snapshot by ID (for side-by-side diff).

    Returns the full verdict JSON and source data for detailed comparison.
    """
    from storage.result_store import result_store

    user = get_current_user(request)
    snapshot = result_store.get_snapshot_by_id(snapshot_id)
    if not snapshot:
        raise HTTPException(status_code=404, detail=f"Snapshot {snapshot_id} not found")
    if not _can_view_snapshot(user, snapshot):
        raise HTTPException(status_code=403, detail="Access denied")
        return JSONResponse(content=_camelize_json(snapshot))


@app.get("/api/v1/debug/sources/{ip}")
async def debug_sources(ip: str):
    """Debug endpoint: show raw plugin results for an IP (always refreshes).

    v2.0: Uses PluginRegistry instead of CollectorAggregator.
    """
    import asyncio
    import yaml

    try:
        from plugins import PluginRegistry, PluginResult, SandboxedPluginRunner

        config_path = os.path.join(
            os.path.dirname(__file__), "..", "config", "plugins.yaml"
        )
        with open(config_path, "r", encoding="utf-8") as f:
            plugin_config = yaml.safe_load(f) or {}

        registry = PluginRegistry(plugin_config)
        registry.discover()
        sandbox_runner = SandboxedPluginRunner()

        plugins = registry.get_enabled("ip")

        async def _safe_query(plugin, observable):
            try:
                if registry.is_sandboxed(plugin.metadata.name):
                    sandbox_config = registry.get_sandbox_config(plugin.metadata.name)
                    return await sandbox_runner.run(
                        plugin, observable, "ip", sandbox_config
                    )
                return await plugin.query(observable, "ip")
            except Exception as e:
                return PluginResult(
                    source=plugin.metadata.name,
                    ok=False,
                    raw_data=None,
                    normalized_data=None,
                    evidence=[],
                    error=str(e),
                )

        results = await asyncio.gather(*[_safe_query(p, ip) for p in plugins])

        summary = {}
        for result in results:
            data = result.raw_data
            summary[result.source] = {
                "ok": result.ok,
                "error": result.error,
                "data_keys": list(data.keys()) if isinstance(data, dict) else None,
                "data_preview": {
                    k: v
                    for k, v in (data or {}).items()
                    if isinstance(v, (int, float, str, bool))
                }
                if isinstance(data, dict)
                else None,
            }

        return JSONResponse(content=_camelize_json(summary))
    except Exception as e:
        raise HTTPException(
            status_code=500, detail=f"Debug collection failed: {str(e)}"
        )


@app.get("/")
async def dashboard(request: Request):
    """Web dashboard for IP analysis."""
    from storage.result_store import result_store

    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    lang = _get_lang(request)
    t = i18n.get_translator(lang)
    recent_queries = result_store.get_user_snapshot_history(user["id"], limit=10)
    recent_reports = result_store.get_user_report_history(user["id"], limit=10)
    response = templates.TemplateResponse(
        "dashboard.html.j2",
        {
            "request": request,
            "t": t,
            "lang": lang,
            "root_path": settings.root_path,
            "user": user,
            "recent_queries": recent_queries,
            "recent_reports": recent_reports,
        },
    )
    response.set_cookie("preferred_locale", lang, max_age=365 * 24 * 3600)
    return response


@app.post("/analyze")
async def analyze_web(
    request: Request, ip: str = Form(...), refresh: bool = Form(False)
):
    """Web form submission for IP analysis."""
    from storage.result_store import result_store

    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    lang = _get_lang(request)
    t = i18n.get_translator(lang)
    try:
        verdict = await service.analyze_ip(ip, refresh=refresh, user_id=user["id"])
        html_report = html_reporter.generate(verdict, lang=lang)
        response = templates.TemplateResponse(
            "dashboard.html.j2",
            {
                "request": request,
                "verdict": verdict,
                "html_report": html_report,
                "ip": ip,
                "t": t,
                "lang": lang,
                "root_path": settings.root_path,
                "user": user,
                "recent_queries": result_store.get_user_snapshot_history(
                    user["id"], limit=10
                ),
                "recent_reports": result_store.get_user_report_history(
                    user["id"], limit=10
                ),
            },
        )
    except Exception as e:
        response = templates.TemplateResponse(
            "dashboard.html.j2",
            {
                "request": request,
                "error": str(e),
                "ip": ip,
                "t": t,
                "lang": lang,
                "root_path": settings.root_path,
                "user": user,
                "recent_queries": result_store.get_user_snapshot_history(
                    user["id"], limit=10
                ),
                "recent_reports": result_store.get_user_report_history(
                    user["id"], limit=10
                ),
            },
        )
    response.set_cookie("preferred_locale", lang, max_age=365 * 24 * 3600)
    return response


@app.post("/api/v1/report/generate")
async def generate_report(
    request: Request,
    ip: str = Form(...),
    regenerate: bool = Form(False),
):
    """Generate a detailed narrative threat intelligence report.

    V2 principle: Report generation NEVER triggers new data queries.
    All data must be collected during the analysis phase first.

    If no cached/persisted analysis exists for the IP, returns 404
    with instructions to run analysis first.

    Args:
        ip: The IP address to generate a report for.
        regenerate: If True, bypass cached report and invoke LLM again.
    """
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    lang = _get_lang(request)

    try:
        # Step 1: Load the latest visible persisted snapshot verdict when available.
        report_verdict, latest_snapshot = _load_latest_report_verdict(ip, user["id"])
        if report_verdict is None:
            raise HTTPException(
                status_code=404,
                detail=(
                    f"No analysis data found for {ip}. "
                    "Please run an analysis first before generating a report."
                ),
            )

        # Step 2: Check for a cached report (unless regenerate is requested)
        if not regenerate:
            from storage.result_store import result_store

            llm_settings = admin_db.resolve_effective_llm_access(user["id"])
            snapshot_id = latest_snapshot.get("id") if latest_snapshot else None

            cached_report = result_store.get_latest_report(
                ip=ip,
                user_id=user["id"],
                llm_fingerprint=llm_settings.get("fingerprint", ""),
                lang=lang,
                snapshot_id=snapshot_id,
            )
            if cached_report:
                logger.info(
                    "Serving cached report for %s (user=%s, lang=%s)",
                    ip,
                    user["id"],
                    lang,
                )
                return HTMLResponse(content=cached_report["report_html"])

        # Step 3: Generate new report from existing verdict (no new queries)
        #   If regenerating, archive old reports first (preserves history for comparison)
        if regenerate:
            from storage.result_store import result_store as _rs

            archived = _rs.archive_reports(ip=ip, user_id=user["id"])
            if archived:
                logger.info(
                    "Archived %d previous report(s) for %s (user=%s)",
                    archived,
                    ip,
                    user["id"],
                )

        llm_settings = admin_db.resolve_effective_llm_access(user["id"])

        # Resolve query_date from the latest snapshot for staleness detection
        from datetime import datetime

        query_date = None
        if latest_snapshot and latest_snapshot.get("queried_at"):
            try:
                query_date = datetime.fromisoformat(latest_snapshot["queried_at"])
            except (ValueError, TypeError):
                pass

        html, llm_used, llm_fallback = await narrative_reporter.generate(
            report_verdict,
            lang=lang,
            llm_overrides=llm_settings,
            query_date=query_date,
        )

        # Step 4: Persist the generated report
        from storage.result_store import result_store

        result_store.save_report(
            ip=ip,
            user_id=user["id"],
            report_html=html,
            llm_enhanced=llm_used,
            llm_fingerprint=llm_settings.get("fingerprint", ""),
            llm_source=llm_settings.get("source", "template"),
            lang=lang,
            snapshot_id=latest_snapshot.get("id") if latest_snapshot else None,
        )

        if llm_fallback:
            logger.warning(
                "Report generation fell back to template mode (ip=%s, user=%s, source=%s, model=%s)",
                ip,
                user["id"],
                llm_settings.get("source", "template"),
                llm_settings.get("model", ""),
            )

        return HTMLResponse(content=html)
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Report generation failed for %s: %s", ip, e)
        raise HTTPException(
            status_code=500, detail=f"Report generation failed: {str(e)}"
        )


@app.get("/api/v1/reports/{ip}/history")
async def get_report_history(ip: str, request: Request, limit: int = 20):
    """Return historical reports for an IP (per-user).

    Supports report comparison — shows how reports evolved over time.
    """
    user = get_current_user(request)
    if not user:
        return JSONResponse(
            status_code=401,
            content=_camelize_json({"detail": "Authentication required"}),
        )

    from storage.result_store import result_store

    try:
        reports = result_store.get_report_history(
            ip=ip, user_id=user["id"], limit=limit
        )
        return JSONResponse(
            content=_camelize_json(
                {"ip": ip, "reports": reports, "count": len(reports)}
            )
        )
    except Exception as e:
        logger.error("Failed to fetch report history for %s: %s", ip, e)
        raise HTTPException(
            status_code=500,
            detail=f"Failed to fetch report history: {str(e)}",
        )


@app.get("/api/v1/results/{ip}/compare")
async def compare_snapshots(
    ip: str,
    request: Request,
    snapshot_a: int | None = None,
    snapshot_b: int | None = None,
):
    """Compare two query snapshots for an IP.

    If snapshot_a and snapshot_b are provided, performs a detailed
    side-by-side diff. Otherwise returns timeline data (score history
    across all snapshots).

    Returns:
        - Timeline mode (no params): list of {queried_at, final_score, level}
        - Diff mode (a & b): detailed field-by-field comparison
    """
    import json as _json

    try:
        user = get_current_user(request)
        if snapshot_a is not None and snapshot_b is not None:
            # Side-by-side diff mode
            snap_a = _load_and_authorize_snapshot(snapshot_a, user)
            snap_b = _load_and_authorize_snapshot(snapshot_b, user)

            # Compute diff
            diff = _compute_snapshot_diff(snap_a, snap_b)
            return JSONResponse(
                content=_camelize_json(
                    {
                        "mode": "diff",
                        "ip": ip,
                        "snapshot_a": {
                            "id": snap_a["id"],
                            "queried_at": snap_a["queried_at"],
                            "final_score": snap_a["final_score"],
                            "level": snap_a["level"],
                        },
                        "snapshot_b": {
                            "id": snap_b["id"],
                            "queried_at": snap_b["queried_at"],
                            "final_score": snap_b["final_score"],
                            "level": snap_b["level"],
                        },
                        "diff": diff,
                    }
                )
            )
        else:
            # Timeline mode — return score history
            snapshots = get_result_store().get_visible_snapshot_history(
                ip=ip,
                user_id=user["id"] if user else None,
                is_admin=bool(user and user.get("is_admin")),
                limit=50,
            )
            timeline = _build_timeline_from_snapshots(snapshots)
            return JSONResponse(
                content=_camelize_json(
                    {
                        "mode": "timeline",
                        "ip": ip,
                        "timeline": timeline,
                        "count": len(timeline),
                    }
                )
            )
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Comparison failed for %s: %s", ip, e)
        raise HTTPException(status_code=500, detail=f"Comparison failed: {str(e)}")


def _compute_snapshot_diff(
    snap_a: dict[str, Any], snap_b: dict[str, Any]
) -> dict[str, Any]:
    """Compute a structured diff between two query snapshots.

    Compares:
      - Score changes (final_score, level)
      - Evidence items (added, removed, changed)
      - Source data differences
    """
    import json as _json

    diff: dict[str, Any] = {
        "score_change": snap_b["final_score"] - snap_a["final_score"],
        "level_change": {
            "from": snap_a["level"],
            "to": snap_b["level"],
            "changed": snap_a["level"] != snap_b["level"],
        },
        "time_delta": {
            "from": snap_a["queried_at"],
            "to": snap_b["queried_at"],
        },
        "evidence_diff": {"added": [], "removed": [], "changed": []},
        "source_diff": {},
    }

    # Parse verdict JSON for evidence comparison
    try:
        verdict_a = _json.loads(snap_a.get("verdict_json", "{}"))
        verdict_b = _json.loads(snap_b.get("verdict_json", "{}"))
    except (TypeError, _json.JSONDecodeError):
        verdict_a = {}
        verdict_b = {}

    # Compare evidence items by source+title key
    evidence_a = {
        f"{e.get('source', '')}::{e.get('title', '')}": e
        for e in verdict_a.get("evidence", [])
    }
    evidence_b = {
        f"{e.get('source', '')}::{e.get('title', '')}": e
        for e in verdict_b.get("evidence", [])
    }

    # Added evidence (in B but not A)
    for key in set(evidence_b) - set(evidence_a):
        diff["evidence_diff"]["added"].append(evidence_b[key])

    # Removed evidence (in A but not B)
    for key in set(evidence_a) - set(evidence_b):
        diff["evidence_diff"]["removed"].append(evidence_a[key])

    # Changed evidence (same key, different score_delta)
    for key in set(evidence_a) & set(evidence_b):
        ea = evidence_a[key]
        eb = evidence_b[key]
        if ea.get("score_delta") != eb.get("score_delta") or ea.get(
            "severity"
        ) != eb.get("severity"):
            diff["evidence_diff"]["changed"].append(
                {
                    "key": key,
                    "from": ea,
                    "to": eb,
                }
            )

    # Compare source data
    try:
        sources_a = _json.loads(snap_a.get("sources_json", "{}") or "{}")
        sources_b = _json.loads(snap_b.get("sources_json", "{}") or "{}")
    except (TypeError, _json.JSONDecodeError):
        sources_a = {}
        sources_b = {}

    all_sources = set(list(sources_a.keys()) + list(sources_b.keys()))
    for source in all_sources:
        sa = sources_a.get(source)
        sb = sources_b.get(source)
        if sa is None and sb is not None:
            diff["source_diff"][source] = {"status": "added"}
        elif sa is not None and sb is None:
            diff["source_diff"][source] = {"status": "removed"}
        elif sa != sb:
            diff["source_diff"][source] = {"status": "changed"}
        # else: unchanged — don't include

    return diff


@app.get("/compare", response_class=HTMLResponse)
async def comparison_page(request: Request, ip: str = ""):
    """Comparison page for viewing score timeline and side-by-side diffs."""
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    lang = _get_lang(request)
    t = i18n.get_translator(lang)
    return templates.TemplateResponse(
        "comparison.html.j2",
        {
            "request": request,
            "t": t,
            "lang": lang,
            "root_path": settings.root_path,
            "user": user,
            "ip": ip,
        },
    )


@app.get("/reports/view/{report_id}", response_class=HTMLResponse)
async def view_report_page(report_id: int, request: Request):
    user = get_current_user(request)
    if not user:
        return login_redirect(request)

    report = get_result_store().get_report_by_id(report_id)
    if not report:
        raise HTTPException(status_code=404, detail=f"Report {report_id} not found")
    if not _can_view_report(user, report):
        raise HTTPException(status_code=403, detail="Access denied")

    return HTMLResponse(content=report["report_html"])


@app.get("/reports/compare", response_class=HTMLResponse)
async def compare_reports_page(
    request: Request,
    report_a: int | None = None,
    report_b: int | None = None,
):
    user = get_current_user(request)
    if not user:
        return login_redirect(request)

    lang = _get_lang(request)
    t = i18n.get_translator(lang)
    reports: list[dict[str, Any]] = []
    for report_id in [report_a, report_b]:
        if report_id is None:
            continue
        report = get_result_store().get_report_by_id(report_id)
        if not report:
            raise HTTPException(status_code=404, detail=f"Report {report_id} not found")
        if not _can_view_report(user, report):
            raise HTTPException(status_code=403, detail="Access denied")
        reports.append(report)

    if len(reports) > 1:
        base_ip = reports[0]["ip"]
        if any(report["ip"] != base_ip for report in reports[1:]):
            raise HTTPException(
                status_code=400,
                detail="Compared reports must belong to the same IP",
            )

    report_history = (
        get_result_store().get_report_history(reports[0]["ip"], user["id"], limit=20)
        if reports
        else []
    )

    return templates.TemplateResponse(
        "reports_compare.html.j2",
        {
            "request": request,
            "t": t,
            "lang": lang,
            "root_path": settings.root_path,
            "user": user,
            "reports": reports,
            "report_history": report_history,
            "report_a": report_a,
            "report_b": report_b,
        },
    )


@app.get("/api/v1/reports/detail/{report_id}")
async def get_report_detail(report_id: int, request: Request):
    """Return a single stored report by ID (for side-by-side comparison)."""
    user = get_current_user(request)
    if not user:
        return JSONResponse(
            status_code=401, content={"detail": "Authentication required"}
        )

    report = get_result_store().get_report_by_id(report_id)
    if not report:
        raise HTTPException(status_code=404, detail=f"Report {report_id} not found")

    if not _can_view_report(user, report):
        raise HTTPException(status_code=403, detail="Access denied")

    return JSONResponse(content=_camelize_json(report))


@app.get("/api/v1/users/me/snapshots")
async def get_my_snapshot_history(request: Request, limit: int = 20):
    user = get_current_user(request)
    if not user:
        return JSONResponse(
            status_code=401,
            content=_camelize_json({"detail": "Authentication required"}),
        )

    snapshots = get_result_store().get_user_snapshot_history(user["id"], limit=limit)
    return JSONResponse(
        content=_camelize_json({"snapshots": snapshots, "count": len(snapshots)})
    )


@app.get("/api/v1/users/me/reports")
async def get_my_report_history(request: Request, limit: int = 20):
    user = get_current_user(request)
    if not user:
        return JSONResponse(
            status_code=401,
            content=_camelize_json({"detail": "Authentication required"}),
        )

    reports = get_result_store().get_user_report_history(user["id"], limit=limit)
    return JSONResponse(
        content=_camelize_json({"reports": reports, "count": len(reports)})
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
