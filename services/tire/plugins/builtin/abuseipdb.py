"""
AbuseIPDB plugin — threat intelligence from AbuseIPDB.

Absorbs:
  - collectors/abuseipdb.py (collection)
  - scoring_rules.yaml sources.abuseipdb (scoring)
"""

import logging
from typing import Any

from models import EvidenceItem
from plugins.base import TIPlugin, PluginMetadata, PluginResult

logger = logging.getLogger(__name__)


class AbuseIPDBPlugin(TIPlugin):
    """Self-contained AbuseIPDB threat intelligence plugin."""

    @property
    def metadata(self) -> PluginMetadata:
        return PluginMetadata(
            name="abuseipdb",
            display_name="AbuseIPDB",
            version="1.0.0",
            supported_types=["ip"],
            requires_api_key=True,
            api_key_env_var="ABUSEIPDB_API_KEY",
            rate_limit=None,
            priority=10,
            tags=["reputation", "abuse"],
            description="IP abuse confidence scoring from AbuseIPDB",
        )

    async def query(self, observable: str, obs_type: str) -> PluginResult:
        api_key = self._get_api_key()
        if not api_key:
            logger.warning("AbuseIPDB API key not configured, skipping query")
            return self._error_result("API key not configured")

        max_age = self.plugin_config.get("max_age_days", 90)
        url = "https://api.abuseipdb.com/api/v2/check"
        headers = {"Accept": "application/json", "Key": api_key}
        params: dict[str, Any] = {
            "ipAddress": observable,
            "maxAgeInDays": max_age,
            "verbose": "true",
        }

        result = await self._make_request(url, headers=headers, params=params)

        if not result["ok"]:
            return self._error_result(result["error"])

        raw = result["data"]
        data = raw.get("data", {})

        # Normalize
        standardized = {
            "abuse_confidence_score": data.get("abuseConfidenceScore", 0),
            "total_reports": data.get("totalReports", 0),
            "country_code": data.get("countryCode"),
            "usage_type": data.get("usageType"),
            "isp": data.get("isp"),
            "domain": data.get("domain"),
            "is_whitelisted": data.get("isWhitelisted", False),
            "last_reported_at": data.get("lastReportedAt"),
            "num_distinct_users": data.get("numDistinctUsers", 0),
        }

        # Score — absorbed from scoring_rules.yaml
        evidence = self._score(standardized)

        return PluginResult(
            source=self.metadata.name,
            ok=True,
            raw_data=standardized,
            normalized_data=None,  # no profile-level normalization
            evidence=evidence,
            error=None,
        )

    def _score(self, data: dict[str, Any]) -> list[EvidenceItem]:
        """Apply scoring rules (from scoring_rules.yaml sources.abuseipdb)."""
        evidence: list[EvidenceItem] = []
        score = data.get("abuse_confidence_score", 0)
        confidence = min(1.0, score / 100.0) if score else 0.0

        if score >= 90:
            evidence.append(
                EvidenceItem(
                    source="abuseipdb",
                    category="reputation",
                    severity="high",
                    title="High abuse confidence",
                    detail=f"AbuseIPDB confidence score: {score}%",
                    score_delta=30,
                    confidence=confidence,
                )
            )
        elif score >= 70:
            evidence.append(
                EvidenceItem(
                    source="abuseipdb",
                    category="reputation",
                    severity="medium",
                    title="Moderate abuse confidence",
                    detail=f"AbuseIPDB confidence score: {score}%",
                    score_delta=20,
                    confidence=confidence,
                )
            )
        elif score > 0:
            evidence.append(
                EvidenceItem(
                    source="abuseipdb",
                    category="reputation",
                    severity="low",
                    title="Low abuse confidence",
                    detail=f"AbuseIPDB confidence score: {score}%",
                    score_delta=5,
                    confidence=confidence,
                )
            )

        return evidence
