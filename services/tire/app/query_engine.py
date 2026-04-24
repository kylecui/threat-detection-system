"""
Query orchestration engine for threat intelligence analysis.

v2.0: Uses PluginRegistry to discover and run TI plugins instead of
the hardcoded CollectorAggregator. Each plugin handles collection,
normalization, AND scoring in a single self-contained unit.

v2.1: Integrates persistent storage — verdicts are archived on refresh
and new results are saved to storage/results.db alongside the TTL cache.
"""

# pyright: reportMissingImports=false, reportImplicitRelativeImport=false, reportMissingModuleSource=false, reportPossiblyUnboundVariable=false

import asyncio
import json
import logging
import os
import time
from datetime import datetime
from typing import Dict, Any, List, Optional, Tuple

import yaml

from models import Observable, ContextProfile, IPProfile, EvidenceItem, Verdict
from enrichers.semantic_enricher import SemanticEnricher
from analyzers.reputation_engine import ReputationEngine
from analyzers.verdict_engine import VerdictEngine
from analyzers.contextual_risk_engine import ContextualRiskEngine
from analyzers.noise_engine import NoiseEngine
from plugins import PluginRegistry, PluginResult, TIPlugin, SandboxedPluginRunner
from cache.cache_store import CacheStore
from storage.result_store import get_result_store
from admin.database import admin_db

logger = logging.getLogger(__name__)

# ── Profile-level field mapping ────────────────────────────────────
# Keys that plugins may return in PluginResult.normalized_data
# and the corresponding IPProfile attribute to set.
_PROFILE_FIELD_MAP = {
    "organization": "organization",
    "country": "country",
    "asn": "asn",
    "network": "network",
    "rdns": "rdns",
    "hostnames": "hostnames",
}


def _load_plugin_config() -> dict[str, Any]:
    """Load plugins.yaml from config/plugins.yaml."""
    config_path = os.path.join(
        os.path.dirname(__file__), "..", "config", "plugins.yaml"
    )
    try:
        with open(config_path, "r", encoding="utf-8") as f:
            return yaml.safe_load(f) or {}
    except Exception as e:
        logger.error(f"Failed to load plugin config: {e}")
        return {}


class QueryEngine:
    """Orchestrates the threat intelligence analysis pipeline.

    v2.0: Replaces CollectorAggregator with PluginRegistry.
    Plugins are auto-discovered from plugins/builtin/ and plugins/community/.
    """

    def __init__(self):
        self.semantic_enricher = SemanticEnricher()
        self.reputation_engine = ReputationEngine()
        self.verdict_engine = VerdictEngine()
        self.cache = CacheStore()
        self.contextual_risk_engine = ContextualRiskEngine()
        self.noise_engine = NoiseEngine()

        # v2.0 — Plugin system
        plugin_config = _load_plugin_config()
        self.registry = PluginRegistry(plugin_config)
        self.registry.discover()

        # v2.0 — Sandbox runner for untrusted plugins
        self._sandbox_runner = SandboxedPluginRunner()

    async def analyze(
        self,
        observable: Observable,
        context: Optional[ContextProfile] = None,
        refresh: bool = False,
        user_id: Optional[int] = None,
    ) -> Verdict:
        """
        Main analysis pipeline.

        Flow:
        1. collect — run enabled plugins concurrently
        2. normalize — apply plugin normalized_data to profile
        3. enrich — add semantic tags
        4. analyze — reputation + contextual + noise analysis
        5. verdict — final decision with evidence fusion

        Args:
            observable: The observable to analyze.
            context: Optional context for enhanced analysis.
            refresh: Bypass cache and re-query all sources.
            user_id: Authenticated user ID for per-user API key resolution.
        """

        if observable.type == "ip":
            # Archive existing snapshots before refresh so history is preserved
            if refresh:
                try:
                    archived = get_result_store().archive_snapshots(observable.value)
                    if archived:
                        logger.info(
                            "Archived %d previous snapshot(s) for %s",
                            archived,
                            observable.value,
                        )
                except Exception as e:
                    logger.warning(
                        "Failed to archive snapshots for %s: %s",
                        observable.value,
                        e,
                    )

            profile, plugin_evidence, sharing_scope = await self._collect_ip_data(
                observable.value, refresh=refresh, user_id=user_id
            )
            enriched_profile = await self._enrich_ip_profile(profile)
            verdict = await self._analyze_ip_profile(
                enriched_profile,
                plugin_evidence,
                context,
                refresh=refresh,
                user_id=user_id,
                sharing_scope=sharing_scope,
            )
            return verdict

        elif observable.type == "domain":
            return Verdict(
                object_type="domain",
                object_value=observable.value,
                reputation_score=0,
                contextual_score=0,
                final_score=0,
                level="Inconclusive",
                confidence=0.0,
                decision="collect_more_context",
                summary="Domain analysis pipeline not implemented",
                evidence=[],
                tags=[],
            )

        elif observable.type == "url":
            return Verdict(
                object_type="url",
                object_value=observable.value,
                reputation_score=0,
                contextual_score=0,
                final_score=0,
                level="Inconclusive",
                confidence=0.0,
                decision="collect_more_context",
                summary="URL analysis pipeline not implemented",
                evidence=[],
                tags=[],
            )

        else:
            raise ValueError(f"Unsupported observable type: {observable.type}")

    # ── Collection via plugins ─────────────────────────────────────

    async def _collect_ip_data(
        self, ip: str, refresh: bool = False, user_id: Optional[int] = None
    ) -> Tuple[IPProfile, List[EvidenceItem], str]:
        """Collect IP data from all enabled plugins.

        Args:
            ip: IP address to query.
            refresh: Bypass cache.
            user_id: Authenticated user ID for per-user API key resolution.

        Returns:
            Tuple of (IPProfile, plugin_evidence_list)
        """
        # Check cache first unless refresh is requested
        if not refresh:
            cached_profile = self.cache.get_normalized_profile(ip)
            if cached_profile:
                return cached_profile, [], "cached"

        # Get enabled plugins for IP observable type
        plugins = self.registry.get_enabled("ip")
        logger.info(
            f"Running {len(plugins)} plugins for IP {ip}: "
            f"{[p.metadata.name for p in plugins]}"
        )

        # Resolve per-user API keys before running plugins
        sharing_scope = "system"
        if user_id is not None:
            key_sources = self._inject_api_keys(plugins, user_id)
            sharing_scope = (
                "personal"
                if any(source == "user" for source in key_sources.values())
                else "shared"
            )
        elif get_result_store().is_configured_only():
            for plugin in plugins:
                plugin.set_api_key_override(None)

        # Run all plugins concurrently with fault-tolerance
        results = await asyncio.gather(
            *[
                self._safe_query(
                    plugin,
                    ip,
                    "ip",
                    user_id=user_id,
                    key_scope=key_sources.get(plugin.metadata.name, "none")
                    if user_id is not None
                    else "system",
                )
                for plugin in plugins
            ]
        )

        # Clear API key overrides after queries complete (security hygiene)
        for plugin in plugins:
            plugin.set_api_key_override(None)

        # Convert PluginResults → legacy sources dict + collect evidence
        sources: Dict[str, Dict[str, Any]] = {}
        all_evidence: List[EvidenceItem] = []

        profile = IPProfile(ip=ip)

        for result in results:
            # Legacy format for backward compat with NoiseEngine,
            # narrative_reporter, and graph/correlator
            sources[result.source] = {
                "source": result.source,
                "ok": result.ok,
                "data": result.raw_data or {},
                "error": result.error,
            }

            # Collect evidence from plugins (pre-scored)
            all_evidence.extend(result.evidence)

            # Apply profile-level normalized data (organization, country, etc.)
            if result.ok and result.normalized_data:
                self._apply_profile_data(profile, result.normalized_data)

        profile.sources = sources
        profile.timestamps = {
            "normalized_at": datetime.now(),
            "collected_at": datetime.now(),
        }

        # Cache raw results and normalized profile
        self.cache.set_collector_results(ip, sources)
        self.cache.set_normalized_profile(ip, profile)

        return profile, all_evidence, sharing_scope

    def _inject_api_keys(self, plugins: List[TIPlugin], user_id: int) -> Dict[str, str]:
        """Resolve per-user API keys and inject them into plugin instances.

        Uses the configured-key fallback chain:
          user_key → shared_admin_key → None.

        Plugins that don't require API keys are skipped.
        """
        key_sources: Dict[str, str] = {}
        for plugin in plugins:
            meta = plugin.metadata
            if not meta.requires_api_key:
                key_sources[meta.name] = "not-required"
                continue

            if meta.name == "greynoise":
                resolved_key = get_result_store().get_plugin_api_key(user_id, meta.name)
                source = "user" if resolved_key else "none"
                if not resolved_key and admin_db.can_user_use_shared_plugin(
                    user_id, meta.name
                ):
                    resolved_key = get_result_store().get_plugin_api_key(0, meta.name)
                    if resolved_key:
                        source = "shared"
            else:
                resolved_key, source = get_result_store().resolve_plugin_api_key(
                    user_id=user_id,
                    plugin_name=meta.name,
                    env_var=meta.api_key_env_var,
                )
                if source == "shared" and not admin_db.can_user_use_shared_plugin(
                    user_id, meta.name
                ):
                    resolved_key, source = None, "none"
            if resolved_key:
                plugin.set_api_key_override(resolved_key)
                logger.debug(
                    "Plugin '%s': API key resolved from %s source",
                    meta.name,
                    source,
                )
            else:
                plugin.set_api_key_override(None)
            key_sources[meta.name] = source
        return key_sources

    async def _safe_query(
        self,
        plugin: TIPlugin,
        observable: str,
        obs_type: str,
        *,
        user_id: int | None = None,
        key_scope: str = "none",
    ) -> PluginResult:
        """Run a single plugin query with fault-tolerance.

        Sandboxed plugins (community/ by default) run in an isolated subprocess.
        Trusted plugins (builtin/ by default) run in-process for zero overhead.
        """
        from prometheus_client import REGISTRY

        tire_plugin_calls_total = REGISTRY._names_to_collectors.get(  # type: ignore[attr-defined]
            "tire_plugin_calls_total"
        )

        plugin_name = plugin.metadata.name
        started_at = time.perf_counter()

        def _status_code_from_error(error: str | None) -> int | None:
            if not error:
                return None
            for token in str(error).replace(":", " ").split():
                if token.isdigit() and len(token) == 3:
                    return int(token)
            return None

        try:
            if self.registry.is_sandboxed(plugin_name):
                sandbox_config = self.registry.get_sandbox_config(plugin_name)
                logger.debug(
                    "Running plugin '%s' in sandbox (timeout=%ds)",
                    plugin_name,
                    sandbox_config.get("timeout", 30),
                )
                result = await self._sandbox_runner.run(
                    plugin, observable, obs_type, sandbox_config
                )
            else:
                result = await plugin.query(observable, obs_type)

            get_result_store().record_plugin_usage(
                plugin_name=plugin_name,
                user_id=user_id,
                key_scope=key_scope,
                success=result.ok,
                latency_ms=int((time.perf_counter() - started_at) * 1000),
                status_code=_status_code_from_error(result.error),
                error_message=result.error or "",
            )
            if tire_plugin_calls_total is not None:
                tire_plugin_calls_total.labels(  # type: ignore[union-attr]
                    plugin_name=plugin_name,
                    status="success" if result.ok else "failure",
                ).inc()
            return result
        except Exception as e:
            logger.error(f"Plugin '{plugin_name}' crashed: {e}", exc_info=True)
            error_message = f"Plugin crashed: {e}"
            get_result_store().record_plugin_usage(
                plugin_name=plugin_name,
                user_id=user_id,
                key_scope=key_scope,
                success=False,
                latency_ms=int((time.perf_counter() - started_at) * 1000),
                error_message=error_message,
            )
            if tire_plugin_calls_total is not None:
                tire_plugin_calls_total.labels(  # type: ignore[union-attr]
                    plugin_name=plugin_name, status="error"
                ).inc()
            return PluginResult(
                source=plugin_name,
                ok=False,
                raw_data=None,
                normalized_data=None,
                evidence=[],
                error=error_message,
            )

    @staticmethod
    def _apply_profile_data(
        profile: IPProfile, normalized_data: Dict[str, Any]
    ) -> None:
        """Apply plugin normalized_data fields to the IPProfile.

        Only sets fields that are present in the plugin's output and
        haven't already been set by a higher-priority plugin.
        """
        for data_key, profile_attr in _PROFILE_FIELD_MAP.items():
            if data_key in normalized_data:
                current = getattr(profile, profile_attr, None)
                if current is None or (isinstance(current, list) and len(current) == 0):
                    setattr(profile, profile_attr, normalized_data[data_key])

    # ── Enrichment ─────────────────────────────────────────────────

    async def _enrich_ip_profile(self, profile: IPProfile) -> IPProfile:
        """Enrich IP profile with semantic information."""
        return self.semantic_enricher.enrich(profile)

    # ── Analysis ───────────────────────────────────────────────────

    async def _analyze_ip_profile(
        self,
        profile: IPProfile,
        plugin_evidence: List[EvidenceItem],
        context: Optional[ContextProfile] = None,
        refresh: bool = False,
        user_id: Optional[int] = None,
        sharing_scope: str = "system",
    ) -> Verdict:
        """Analyze IP profile for threats.

        v2.0 change: Plugins already produced scored evidence. The
        ReputationEngine now only adds semantic tag adjustments.

        Args:
            profile: Enriched IP profile.
            plugin_evidence: Evidence items from plugins.
            context: Optional context for enhanced analysis.
            refresh: Bypass cache.
            user_id: User ID for snapshot attribution.
        """
        # Check cache first unless refresh
        if not refresh:
            cached_verdict = self.cache.get_verdict(profile.ip)
            if cached_verdict:
                return cached_verdict

        # Get reputation score and evidence
        # v2.0: ReputationEngine still evaluates source-specific rules
        # from scoring_rules.yaml AND applies semantic adjustments.
        # Plugin evidence is merged in below.
        reputation_score, reputation_evidence = self.reputation_engine.analyze(
            profile, plugin_evidence=plugin_evidence
        )

        # Get contextual score and evidence
        contextual_adjustment, contextual_evidence = (
            self.contextual_risk_engine.analyze(profile, context)
        )
        contextual_score = contextual_adjustment

        # Get noise analysis
        noise_score, noise_classification, noise_evidence = self.noise_engine.analyze(
            profile
        )

        # Combine evidence: plugin + reputation + contextual + noise
        all_evidence = (
            plugin_evidence + reputation_evidence + contextual_evidence + noise_evidence
        )

        # Sum plugin evidence scores into reputation_score
        plugin_score = sum(e.score_delta for e in plugin_evidence)
        total_reputation_score = reputation_score + plugin_score

        # Generate verdict
        verdict = self.verdict_engine.generate_verdict(
            object_type="ip",
            object_value=profile.ip,
            reputation_score=total_reputation_score,
            contextual_score=contextual_score,
            evidence=all_evidence,
            tags=profile.tags,
            raw_sources=profile.sources,
        )

        # Cache verdict
        self.cache.set_verdict(profile.ip, verdict)

        # Persist verdict snapshot for historical comparison
        try:
            verdict_json = verdict.model_dump_json(by_alias=True)
            sources_json = json.dumps(profile.sources) if profile.sources else None
            # Determine API key type for per-key sharing logic
            api_key_type = sharing_scope
            get_result_store().save_snapshot(
                ip=profile.ip,
                verdict_json=verdict_json,
                sources_json=sources_json,
                final_score=verdict.final_score,
                level=verdict.level,
                user_id=user_id,
                api_key_type=api_key_type,
            )
        except Exception as e:
            logger.warning("Failed to persist snapshot for %s: %s", profile.ip, e)

        return verdict
