"""
TianJi YouMeng (天际友盟) plugin — threat intelligence from RedQueen platform.

TianJi YouMeng is an enterprise-only Chinese threat intelligence provider.
Their API is accessed through the RedQueen platform at https://redqueen.tj-un.com.

This plugin implements the RedQueen TI API which returns STIX-compatible
threat intelligence data. No free tier is available — a commercial
subscription is required.

Contact: service@tj-un.com
Platform: https://redqueen.tj-un.com
"""

import logging
from typing import Any

from models import EvidenceItem
from plugins.base import TIPlugin, PluginMetadata, PluginResult

logger = logging.getLogger(__name__)


class TianJiYouMengPlugin(TIPlugin):
    """Self-contained TianJi YouMeng (天际友盟) threat intelligence plugin.

    Queries the RedQueen platform API for IP threat intelligence.
    Returns STIX-compatible threat intelligence data including:
    - Threat type and severity
    - Confidence scores
    - Related indicators and campaigns
    - First/last seen timestamps

    This is an enterprise-only service. No free tier is available.
    Contact service@tj-un.com for API access.
    Platform: https://redqueen.tj-un.com
    """

    API_BASE = "https://redqueen.tj-un.com/api/v1"

    @property
    def metadata(self) -> PluginMetadata:
        return PluginMetadata(
            name="tianjiyoumeng",
            display_name="TianJi YouMeng (天际友盟)",
            version="1.0.0",
            supported_types=["ip"],
            requires_api_key=True,
            api_key_env_var="TIANJIYOUMENG_API_KEY",
            rate_limit=None,
            priority=15,
            tags=["reputation", "threat-intelligence", "chinese-ti", "enterprise"],
            description="IP threat intelligence from TianJi YouMeng RedQueen platform (天际友盟)",
        )

    async def query(self, observable: str, obs_type: str) -> PluginResult:
        """Query TianJi YouMeng RedQueen API for IP threat intelligence.

        The RedQueen API uses Bearer token authentication and returns
        STIX-compatible threat data.

        Note: The exact API contract may vary by subscription tier.
        This implementation follows the documented RedQueen TI API pattern.
        If you encounter authentication or format issues, contact
        service@tj-un.com for your specific API documentation.
        """
        api_key = self._get_api_key()
        if not api_key:
            logger.warning(
                "TianJi YouMeng API key not configured, skipping query. "
                "This is an enterprise-only service — contact service@tj-un.com"
            )
            return self._error_result(
                "API key not configured. TianJi YouMeng is enterprise-only — "
                "contact service@tj-un.com for access."
            )

        # RedQueen API endpoint for indicator lookup
        url = f"{self.API_BASE}/indicators"
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Accept": "application/json",
            "Content-Type": "application/json",
        }
        params: dict[str, Any] = {
            "type": "ipv4-addr",
            "value": observable,
        }

        result = await self._make_request(
            url,
            method="GET",
            headers=headers,
            params=params,
            timeout=self.plugin_config.get("timeout", 20.0),
        )

        if not result["ok"]:
            return self._error_result(result["error"])

        raw = result["data"]

        # Handle various response structures
        # RedQueen may return data under "data", "objects", or top-level
        indicators = self._extract_indicators(raw)

        if not indicators:
            # No threat data found — this is a valid result (IP is clean)
            return PluginResult(
                source=self.metadata.name,
                ok=True,
                raw_data={"ip": observable, "indicators": [], "threat_found": False},
                normalized_data=None,
                evidence=[],
                error=None,
            )

        # Normalize
        standardized = self._normalize(indicators, observable)

        # Score
        evidence = self._score(standardized)

        return PluginResult(
            source=self.metadata.name,
            ok=True,
            raw_data=standardized,
            normalized_data=None,
            evidence=evidence,
            error=None,
        )

    def _extract_indicators(self, raw: dict[str, Any]) -> list[dict[str, Any]]:
        """Extract threat indicators from various RedQueen response formats.

        The API may return data in STIX bundle format or a custom wrapper.
        This method handles both.
        """
        # STIX bundle format
        if raw.get("type") == "bundle":
            objects = raw.get("objects", [])
            return [
                obj
                for obj in objects
                if obj.get("type")
                in ("indicator", "malware", "attack-pattern", "threat-actor")
            ]

        # Custom wrapper format
        if "data" in raw:
            data = raw["data"]
            if isinstance(data, list):
                return data
            if isinstance(data, dict):
                return data.get("indicators", data.get("objects", []))

        # Direct list
        if "indicators" in raw:
            indicators = raw["indicators"]
            return indicators if isinstance(indicators, list) else []

        if "objects" in raw:
            objects = raw["objects"]
            return objects if isinstance(objects, list) else []

        return []

    def _normalize(
        self, indicators: list[dict[str, Any]], observable: str
    ) -> dict[str, Any]:
        """Normalize RedQueen API response into a standard format."""
        threat_types: list[str] = []
        labels: list[str] = []
        max_confidence = 0
        first_seen = ""
        last_seen = ""

        for indicator in indicators:
            # STIX-style fields
            if "labels" in indicator:
                labels.extend(indicator["labels"])
            if "indicator_types" in indicator:
                threat_types.extend(indicator["indicator_types"])

            # Confidence
            conf = indicator.get("confidence", 0)
            if isinstance(conf, (int, float)) and conf > max_confidence:
                max_confidence = int(conf)

            # Timestamps
            fs = indicator.get("first_seen", indicator.get("created", ""))
            ls = indicator.get("last_seen", indicator.get("modified", ""))
            if fs and (not first_seen or fs < first_seen):
                first_seen = fs
            if ls and (not last_seen or ls > last_seen):
                last_seen = ls

            # Custom fields from RedQueen
            if "threat_type" in indicator:
                threat_types.append(indicator["threat_type"])
            if "tags" in indicator and isinstance(indicator["tags"], list):
                labels.extend(indicator["tags"])

        # Deduplicate
        threat_types = list(dict.fromkeys(threat_types))
        labels = list(dict.fromkeys(labels))

        return {
            "ip": observable,
            "threat_found": True,
            "indicator_count": len(indicators),
            "threat_types": threat_types,
            "labels": labels,
            "confidence": max_confidence,
            "first_seen": first_seen,
            "last_seen": last_seen,
            "indicators": indicators,
        }

    def _score(self, data: dict[str, Any]) -> list[EvidenceItem]:
        """Score based on RedQueen threat intelligence data.

        Scoring is based on:
        1. Number of indicators found
        2. Confidence level from the platform
        3. Severity of threat types (malware > suspicious > anomalous)
        """
        evidence: list[EvidenceItem] = []

        if not data.get("threat_found"):
            return evidence

        indicator_count = data.get("indicator_count", 0)
        confidence = data.get("confidence", 0)
        threat_types = data.get("threat_types", [])
        labels = data.get("labels", [])

        # Build detail
        detail_parts = [f"RedQueen found {indicator_count} threat indicator(s)"]
        if threat_types:
            detail_parts.append(f"Types: {', '.join(threat_types[:5])}")
        if labels:
            detail_parts.append(f"Labels: {', '.join(labels[:5])}")
        if confidence:
            detail_parts.append(f"Platform confidence: {confidence}%")
        detail = ". ".join(detail_parts)

        # Determine severity from threat types and confidence
        high_severity_types = {
            "malware",
            "c2",
            "command-and-control",
            "ransomware",
            "apt",
            "botnet",
            "exploit",
        }
        medium_severity_types = {
            "suspicious",
            "phishing",
            "spam",
            "scanner",
            "proxy",
            "tor",
            "vpn",
        }

        # Check if any high-severity threat types match
        types_lower = {t.lower() for t in threat_types}
        labels_lower = {lb.lower() for lb in labels}
        all_indicators = types_lower | labels_lower

        has_high = bool(all_indicators & high_severity_types)
        has_medium = bool(all_indicators & medium_severity_types)

        if has_high or confidence >= 80:
            evidence.append(
                EvidenceItem(
                    source="tianjiyoumeng",
                    category="reputation",
                    severity="high",
                    title="High threat indicators (RedQueen)",
                    detail=detail,
                    score_delta=25,
                    confidence=min(1.0, confidence / 100.0) if confidence else 0.8,
                )
            )
        elif has_medium or confidence >= 50:
            evidence.append(
                EvidenceItem(
                    source="tianjiyoumeng",
                    category="reputation",
                    severity="medium",
                    title="Moderate threat indicators (RedQueen)",
                    detail=detail,
                    score_delta=15,
                    confidence=min(1.0, confidence / 100.0) if confidence else 0.6,
                )
            )
        elif indicator_count > 0:
            evidence.append(
                EvidenceItem(
                    source="tianjiyoumeng",
                    category="reputation",
                    severity="low",
                    title="Low threat indicators (RedQueen)",
                    detail=detail,
                    score_delta=5,
                    confidence=min(1.0, confidence / 100.0) if confidence else 0.4,
                )
            )

        return evidence
