"""
Example Community Plugin — ThreatFox IOC Lookup
================================================

This file is a **developer reference** for writing your own TIRE plugins.
It demonstrates the full plugin lifecycle using the free ThreatFox API
(https://threatfox.abuse.ch/) which requires NO API key.

HOW TO CREATE YOUR OWN PLUGIN:
    1. Copy this file and rename it (e.g., my_vendor.py)
    2. Place it in plugins/community/ (auto-discovered on startup)
    3. Update the PluginMetadata in the `metadata` property
    4. Implement your API call in `query()`
    5. Write your scoring logic in `_score()`
    6. Add a config entry in config/plugins.yaml
    7. Restart TIRE

PLUGIN CONTRACT:
    - Your class MUST extend TIPlugin
    - You MUST implement the `metadata` property → PluginMetadata
    - You MUST implement `async query(observable, obs_type)` → PluginResult
    - Use `self._make_request()` for HTTP calls (built-in retries + error handling)
    - Use `self._get_api_key()` to resolve API keys from environment variables
    - Use `self._error_result(msg)` to return a failed PluginResult
    - Use `self.plugin_config` to access your per-plugin config from plugins.yaml

WHAT NOT TO DO:
    - Do NOT import external libraries not in requirements.txt
    - Do NOT access global state or modify os.environ
    - Do NOT write to the filesystem
    - Do NOT spawn threads or subprocesses
    - Community plugins run in a sandboxed subprocess — keep them self-contained
"""

import logging
from typing import Any

from models import EvidenceItem
from plugins.base import TIPlugin, PluginMetadata, PluginResult

logger = logging.getLogger(__name__)


class ThreatFoxPlugin(TIPlugin):
    """Example plugin: query ThreatFox for known IOC associations.

    ThreatFox is a free, open-source IOC database by abuse.ch.
    API docs: https://threatfox.abuse.ch/api/

    This plugin checks if an IP address is associated with known malware
    command-and-control (C2) servers or other malicious infrastructure.
    """

    # ── Step 1: Define your plugin metadata ─────────────────────
    #
    # The `metadata` property tells TIRE about your plugin's identity,
    # capabilities, and requirements. The registry uses this to decide
    # when to run your plugin and how to configure it.

    @property
    def metadata(self) -> PluginMetadata:
        return PluginMetadata(
            # Unique identifier — must match your config/plugins.yaml key
            name="example_threatfox",
            # Human-readable name shown in the UI and reports
            display_name="ThreatFox (Example)",
            # Semver version of YOUR plugin (not the API)
            version="1.0.0",
            # Which observable types this plugin can handle.
            # TIRE will only call your plugin for matching types.
            # Options: "ip", "domain", "url", "hash"
            supported_types=["ip"],
            # Set True if your API requires an API key.
            # ThreatFox is free — no key needed.
            requires_api_key=False,
            # Environment variable name holding the API key.
            # Set to None if no key is needed.
            api_key_env_var=None,
            # Rate limit in requests/second, or None for unlimited.
            rate_limit=None,
            # Execution priority: lower = runs first. Use 99 for demos.
            # Builtin sources (AbuseIPDB, VirusTotal) use 10-30.
            priority=99,
            # Tags for categorization and filtering
            tags=["example", "malware", "c2"],
            # Brief description shown in plugin listings
            description="Check IP against ThreatFox IOC database (example plugin)",
        )

    # ── Step 2: Implement the query method ──────────────────────
    #
    # This is the core of your plugin. It must:
    #   1. Call your external API
    #   2. Parse and normalize the response
    #   3. Generate scored evidence items
    #
    # It receives:
    #   - observable: the value to look up (e.g., "8.8.8.8")
    #   - obs_type: the type ("ip", "domain", etc.)
    #
    # It must return a PluginResult.

    async def query(self, observable: str, obs_type: str) -> PluginResult:
        # --- API Call ---
        # ThreatFox uses a POST endpoint with a JSON body.
        # Adapt this to your vendor's API format.
        url = "https://threatfox-api.abuse.ch/api/v1/"
        json_body = {
            "query": "search_ioc",
            "search_term": observable,
        }

        # Use the built-in _make_request() for automatic retries and
        # error handling. It returns {"ok": bool, "data": dict, "error": str}.
        # For POST requests, pass method="POST" and json_body=...
        result = await self._make_request(
            url,
            method="POST",
            json_body=json_body,
            timeout=self.plugin_config.get("timeout_seconds", 10),
        )

        # If the HTTP request failed, return an error result.
        # _error_result() is a convenience method from the base class.
        if not result["ok"]:
            return self._error_result(result["error"])

        raw = result["data"]

        # --- Response Parsing ---
        # ThreatFox returns: {"query_status": "ok", "data": [...]}
        # or {"query_status": "no_result"} if not found.
        query_status = raw.get("query_status", "")
        iocs = raw.get("data", []) if query_status == "ok" else []

        # Build a normalized summary dict.
        # This is what ends up in PluginResult.raw_data and is available
        # to the report generator (narrative_reporter) via verdict.raw_sources.
        normalized = {
            "query_status": query_status,
            "ioc_count": len(iocs) if isinstance(iocs, list) else 0,
            "malware_families": [],
            "threat_types": [],
            "confidence_levels": [],
        }

        if isinstance(iocs, list):
            for ioc in iocs:
                malware = ioc.get("malware_printable", "")
                threat_type = ioc.get("threat_type", "")
                confidence = ioc.get("confidence_level", 0)
                if malware and malware not in normalized["malware_families"]:
                    normalized["malware_families"].append(malware)
                if threat_type and threat_type not in normalized["threat_types"]:
                    normalized["threat_types"].append(threat_type)
                if confidence:
                    normalized["confidence_levels"].append(confidence)

        # --- Scoring ---
        # Generate evidence items based on what we found.
        evidence = self._score(normalized)

        return PluginResult(
            source=self.metadata.name,
            ok=True,
            raw_data=normalized,
            # normalized_data is for profile-level fields (organization, country, etc.)
            # Most plugins leave this as None — only set it if your source
            # provides ownership/geolocation data.
            normalized_data=None,
            evidence=evidence,
            error=None,
        )

    # ── Step 3: Implement scoring logic ─────────────────────────
    #
    # This is where you translate your API's data into EvidenceItem
    # objects that feed into TIRE's verdict engine.
    #
    # Each EvidenceItem has:
    #   - source:      your plugin name (for attribution)
    #   - category:    "reputation", "context", "infrastructure", etc.
    #   - severity:    "critical", "high", "medium", "low", "info"
    #   - title:       short label shown in the verdict summary
    #   - detail:      longer explanation shown in evidence details
    #   - score_delta: how much to adjust the threat score (0-100 scale)
    #                  positive = more dangerous, negative = less dangerous
    #   - confidence:  how confident this evidence is (0.0 to 1.0)
    #   - raw:         optional dict with extra data for debugging

    def _score(self, data: dict[str, Any]) -> list[EvidenceItem]:
        evidence: list[EvidenceItem] = []
        ioc_count = data.get("ioc_count", 0)
        malware_families = data.get("malware_families", [])
        threat_types = data.get("threat_types", [])

        if ioc_count == 0:
            # No IOCs found — this is a GOOD signal. You can optionally
            # emit a low-severity "clean" evidence item, or return nothing.
            return evidence

        # --- High severity: known C2 infrastructure ---
        if "botnet_cc" in threat_types:
            evidence.append(
                EvidenceItem(
                    source=self.metadata.name,
                    category="reputation",
                    severity="high",
                    title="Known C2 infrastructure",
                    detail=(
                        f"ThreatFox: IP associated with {ioc_count} IOC(s), "
                        f"including botnet C2. Malware: {', '.join(malware_families[:3])}"
                    ),
                    score_delta=35,
                    confidence=0.85,
                    raw={"ioc_count": ioc_count, "threat_types": threat_types},
                )
            )
        # --- Medium severity: other malware associations ---
        elif malware_families:
            evidence.append(
                EvidenceItem(
                    source=self.metadata.name,
                    category="reputation",
                    severity="medium",
                    title="Malware association",
                    detail=(
                        f"ThreatFox: IP linked to {ioc_count} IOC(s). "
                        f"Malware families: {', '.join(malware_families[:5])}"
                    ),
                    score_delta=20,
                    confidence=0.7,
                    raw={"ioc_count": ioc_count, "malware_families": malware_families},
                )
            )
        # --- Low severity: listed but no strong signal ---
        else:
            evidence.append(
                EvidenceItem(
                    source=self.metadata.name,
                    category="reputation",
                    severity="low",
                    title="ThreatFox listing",
                    detail=f"ThreatFox: IP appears in {ioc_count} IOC record(s)",
                    score_delta=5,
                    confidence=0.5,
                )
            )

        return evidence
