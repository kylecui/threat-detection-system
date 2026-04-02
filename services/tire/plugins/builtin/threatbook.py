"""
ThreatBook (微步在线) plugin — threat intelligence from ThreatBook API.

Uses the ThreatBook Scene IP Reputation API (v3) to query IP threat
information including severity level, judgments, tags, and basic info.

API documentation: https://x.threatbook.com/v5/myApi
Free tier: 50 queries/day after registration.
"""

import logging
from typing import Any

from models import EvidenceItem
from plugins.base import TIPlugin, PluginMetadata, PluginResult

logger = logging.getLogger(__name__)


class ThreatBookPlugin(TIPlugin):
    """Self-contained ThreatBook (微步在线) threat intelligence plugin.

    Queries the ThreatBook Scene IP Reputation API to obtain:
    - Severity level (info / low / medium / high / critical)
    - Judgments (malicious, suspicious, etc.)
    - Tags and tag classes (e.g. C2, Scanner, Proxy)
    - Basic IP info (carrier, location)

    Registration: https://x.threatbook.com/v5/myApi
    """

    API_BASE = "https://api.threatbook.cn/v3"
    SEVERITY_MAP = {
        "critical": "critical",
        "严重": "critical",
        "high": "high",
        "高": "high",
        "medium": "medium",
        "中": "medium",
        "moderate": "medium",
        "low": "low",
        "低": "low",
        "info": "info",
        "information": "info",
        "信息": "info",
        "safe": "info",
        "unknown": "info",
        "未知": "info",
    }

    @property
    def metadata(self) -> PluginMetadata:
        return PluginMetadata(
            name="threatbook",
            display_name="ThreatBook (微步在线)",
            version="1.0.0",
            supported_types=["ip"],
            requires_api_key=True,
            api_key_env_var="THREATBOOK_API_KEY",
            rate_limit=None,
            priority=15,
            tags=["reputation", "threat-intelligence", "chinese-ti"],
            description="IP threat intelligence from ThreatBook (微步在线)",
        )

    async def query(self, observable: str, obs_type: str) -> PluginResult:
        """Query ThreatBook Scene IP Reputation API.

        API endpoint: POST /v3/scene/ip_reputation
        Parameters:
            apikey: API key (query param)
            resource: IP address to query
            lang: Response language (zh/en)
        """
        api_key = self._get_api_key()
        if not api_key:
            logger.warning("ThreatBook API key not configured, skipping query")
            return self._error_result("API key not configured")

        url = f"{self.API_BASE}/scene/ip_reputation"
        lang = self.plugin_config.get("lang", "zh")

        # ThreatBook uses POST with form-encoded params including apikey
        result = await self._make_request(
            url,
            method="POST",
            params={"apikey": api_key, "resource": observable, "lang": lang},
            timeout=self.plugin_config.get("timeout", 15.0),
        )

        if not result["ok"]:
            return self._error_result(result["error"])

        raw = result["data"]

        # ThreatBook wraps response under response_code and data
        response_code = raw.get("response_code", -1)
        if response_code != 0:
            error_msg = raw.get("verbose_msg", f"API error code: {response_code}")
            return self._error_result(error_msg)

        ip_data = raw.get("data", {}).get(observable, {})
        if not ip_data:
            return self._error_result(f"No data returned for {observable}")

        # Normalize response
        standardized = self._normalize(ip_data, observable)

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

    def _normalize(self, ip_data: dict[str, Any], observable: str) -> dict[str, Any]:
        """Normalize ThreatBook API response into a standard format."""
        severity = self._normalize_severity(ip_data.get("severity", "info"))
        judgments = ip_data.get("judgments", [])

        # Extract tags
        tags_classes = ip_data.get("tags_classes", [])
        tags = []
        for tag_class in tags_classes:
            tags.extend(tag_class.get("tags", []))

        # Basic info
        basic = ip_data.get("basic", {})
        carrier = basic.get("carrier", "")
        location = basic.get("location", {})
        country = location.get("country", "")
        province = location.get("province", "")
        city = location.get("city", "")

        # Intelligences summary
        intelligences = ip_data.get("intelligences", {})
        intel_sources = (
            list(intelligences.keys()) if isinstance(intelligences, dict) else []
        )

        return {
            "ip": observable,
            "severity": severity,
            "judgments": judgments,
            "tags": tags,
            "tags_classes": tags_classes,
            "carrier": carrier,
            "country": country,
            "province": province,
            "city": city,
            "intel_sources": intel_sources,
            "update_time": ip_data.get("update_time", ""),
            "is_malicious": any(j in ("malicious", "Malicious") for j in judgments),
            "is_suspicious": any(j in ("suspicious", "Suspicious") for j in judgments),
        }

    def _normalize_severity(self, severity: Any) -> str:
        """Normalize ThreatBook severity labels across language variants."""
        if not isinstance(severity, str):
            return "info"
        return self.SEVERITY_MAP.get(severity.strip().lower(), "info")

    def _score(self, data: dict[str, Any]) -> list[EvidenceItem]:
        """Apply scoring rules based on ThreatBook severity and judgments.

        Severity mapping:
            critical → score_delta 35, high confidence
            high     → score_delta 25, high confidence
            medium   → score_delta 15, medium confidence
            low      → score_delta 5, low confidence
            info     → score_delta 0
        """
        evidence: list[EvidenceItem] = []
        severity = data.get("severity", "info").lower()
        judgments = data.get("judgments", [])
        tags = data.get("tags", [])

        # Build detail string
        detail_parts = [f"ThreatBook severity: {severity}"]
        if judgments:
            detail_parts.append(f"Judgments: {', '.join(str(j) for j in judgments)}")
        if tags:
            detail_parts.append(f"Tags: {', '.join(str(t) for t in tags[:5])}")
        detail = ". ".join(detail_parts)

        if severity == "critical":
            evidence.append(
                EvidenceItem(
                    source="threatbook",
                    category="reputation",
                    severity="critical",
                    title="Critical threat level (ThreatBook)",
                    detail=detail,
                    score_delta=35,
                    confidence=0.95,
                )
            )
        elif severity == "high":
            evidence.append(
                EvidenceItem(
                    source="threatbook",
                    category="reputation",
                    severity="high",
                    title="High threat level (ThreatBook)",
                    detail=detail,
                    score_delta=25,
                    confidence=0.85,
                )
            )
        elif severity == "medium":
            evidence.append(
                EvidenceItem(
                    source="threatbook",
                    category="reputation",
                    severity="medium",
                    title="Moderate threat level (ThreatBook)",
                    detail=detail,
                    score_delta=15,
                    confidence=0.7,
                )
            )
        elif severity == "low":
            evidence.append(
                EvidenceItem(
                    source="threatbook",
                    category="reputation",
                    severity="low",
                    title="Low threat level (ThreatBook)",
                    detail=detail,
                    score_delta=5,
                    confidence=0.5,
                )
            )
        # info severity → no evidence (benign)

        # Additional evidence for specific malicious judgments
        if data.get("is_malicious") and severity not in ("critical", "high"):
            evidence.append(
                EvidenceItem(
                    source="threatbook",
                    category="reputation",
                    severity="high",
                    title="Malicious judgment (ThreatBook)",
                    detail=f"ThreatBook explicitly judged this IP as malicious. {detail}",
                    score_delta=20,
                    confidence=0.8,
                )
            )

        return evidence
